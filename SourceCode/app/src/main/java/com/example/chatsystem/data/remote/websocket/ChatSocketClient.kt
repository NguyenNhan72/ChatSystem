package com.example.chatsystem.data.remote.websocket

import android.util.Log
import com.example.chat.protocol.AckEvent
import com.example.chat.protocol.ClientEvent
import com.example.chat.protocol.ConnectedEvent
import com.example.chat.protocol.ErrorEvent
import com.example.chat.protocol.HeartbeatEvent
import com.example.chat.protocol.MessageEvent
import com.example.chat.protocol.Protocol
import com.example.chat.protocol.ReceiptEvent
import com.example.chat.protocol.ServerEvent
import com.example.chatsystem.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Ket noi WebSocket ben vung duy nhat toi chat server (sach: client <-> chat server, Hinh 12-8).
 * - Xac thuc thong qua OkHttp client dung chung, interceptor tu them header JWT vao HTTP upgrade request.
 * - Tu dong ket noi lai bang co che exponential backoff (1s, 2s, 4s... toi da 30s) cho den khi goi [disconnect].
 * - Sach, Wrap up: sau [DISCOVER_AFTER] lan that bai lien tiep, hoi service discovery ([discover]) xin chat server khac.
 * - Chuyen moi server event theo dung thu tu den toi consumer thong qua Flow [events].
 */
class ChatSocketClient(
    baseClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val discover: suspend (failedUrl: String) -> String? = { null },
) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    // Ping tu phia client: phat hien rot mang sau khoang 20s ke ca khi server chua kip phan hoi.
    private val client = baseClient.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    // Channel khong gioi han: luong doc cua OkHttp khong bi block va khong bao gio bi roi hay lech thu tu event.
    private val channel = Channel<ServerEvent>(Channel.UNLIMITED)
    val events: Flow<ServerEvent> = channel.receiveAsFlow()

    private var url: String? = null          // null = khong duy tri ket noi (chua dang nhap / da logout)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var attempt = 0

    @Synchronized
    fun connect(url: String) {
        this.url = url
        attempt = 0
        if (socket == null) open()
    }

    @Synchronized
    fun disconnect() {
        url = null
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, "logout") // server se danh dau offline ngay lap tuc (Hinh 12-17)
        socket = null
        _state.value = State.DISCONNECTED
    }

    /** Bo qua thoi gian cho backoff con lai, vi du khi Android bao mang da hoat dong tro lai. */
    @Synchronized
    fun reconnectNow() {
        if (url == null || socket != null) return
        reconnectJob?.cancel()
        attempt = 0
        open()
    }

    /**
     * Android bao mat ket noi mang: ngat socket ngay thay vi phai cho 20-40s de ping timeout.
     * UI lap tuc hien thi trang thai mat ket noi va tin moi se nam lai trong outbox thay vi gui vao socket chet.
     */
    @Synchronized
    fun onNetworkLost() {
        socket?.cancel() // -> onFailure -> dropped(): backoff thu lai cho den khi co mang (reconnectNow)
    }

    /** Tra ve false khi chua ket noi: noi goi se giu tin trong outbox va gui lai sau khi co event connected. */
    fun send(event: ClientEvent): Boolean {
        val s = synchronized(this) { socket.takeIf { _state.value == State.CONNECTED } } ?: return false
        if (event !is HeartbeatEvent) Log.d(AppContainer.TAG, "WS -> ${describe(event)}")
        return s.send(Protocol.encode(event))
    }

    private fun open() { // ham goi da giu khoa synchronized
        val target = url ?: return
        _state.value = State.CONNECTING
        Log.i(AppContainer.TAG, "WS connecting to $target (attempt ${attempt + 1})")
        socket = client.newWebSocket(Request.Builder().url(target).build(), Listener())
    }

    @Synchronized
    private fun dropped(ws: WebSocket, reason: String, retry: Boolean) {
        if (ws !== socket) return // socket cu da bi thay the hoac da chu dong dong
        socket = null
        heartbeatJob?.cancel()
        _state.value = State.DISCONNECTED
        if (url == null || !retry) return
        val delayMs = backoffDelayMs(attempt)
        attempt++
        Log.w(AppContainer.TAG, "WS dropped ($reason), retry in ${delayMs / 1000} s")
        val failedUrl = url
        val askDiscovery = attempt % DISCOVER_AFTER == 0
        reconnectJob = scope.launch {
            delay(delayMs)
            val next = if (askDiscovery && failedUrl != null) discover(failedUrl) else null
            synchronized(this@ChatSocketClient) {
                if (next != null && url == failedUrl && next != failedUrl) {
                    Log.i(AppContainer.TAG, "Service discovery: switching chat server to $next")
                    url = next
                }
                if (socket == null) open()
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { Protocol.decodeServer(text) }.getOrElse {
                Log.w(AppContainer.TAG, "WS unreadable frame ignored", it)
                return
            }
            Log.d(AppContainer.TAG, "WS <- ${describe(event)}")
            if (event is ConnectedEvent) {
                synchronized(this@ChatSocketClient) {
                    attempt = 0
                    heartbeatJob?.cancel()
                    // Hinh 12-18: gui heartbeat moi 5s; server se coi la offline neu qua 30s khong co tin hieu.
                    heartbeatJob = scope.launch {
                        while (true) {
                            delay(HEARTBEAT_MS)
                            send(HeartbeatEvent)
                        }
                    }
                }
                _state.value = State.CONNECTED
            }
            channel.trySend(event)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket, "closed $code", retry = true)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            // 401: token bi tu choi — thu lai khong co ich; xoa session de app tu dong dang nhap lai.
            dropped(webSocket, response?.let { "HTTP ${it.code}" } ?: t.toString(), retry = response?.code != 401)
    }

    /** Format dong log khong chua noi dung tin nhan (tranh lo thong tin ca nhan tren Logcat). */
    private fun describe(e: Any): String = when (e) {
        is AckEvent -> "ack conv=${e.conversationId} seq=${e.seq}"
        is MessageEvent -> "message conv=${e.message.conversationId} seq=${e.message.seq} from=${e.message.senderId}"
        is com.example.chat.protocol.PresenceEvent -> "presence user=${e.userId} online=${e.online}"
        is ReceiptEvent -> "receipt conv=${e.conversationId} user=${e.userId} delivered=${e.deliveredSeq} read=${e.readSeq}"
        is ErrorEvent -> "error ${e.code}"
        is com.example.chat.protocol.SendEvent -> "send conv=${e.conversationId} id=${e.clientMsgId.take(8)}"
        is com.example.chat.protocol.DeliveredEvent -> "delivered conv=${e.conversationId} seq=${e.seq}"
        is com.example.chat.protocol.ReadEvent -> "read conv=${e.conversationId} seq=${e.seq}"
        else -> e.toString()
    }
}

const val HEARTBEAT_MS = 5_000L
const val DISCOVER_AFTER = 3 // so lan ket noi that bai lien tiep truoc khi coi chat server la da loi

/** Thoi gian cho ket noi lai sau lan that bai thu [attempt] (bat dau tu 0): 1, 2, 4, 8, 16, 30, 30... giay. */
fun backoffDelayMs(attempt: Int): Long = minOf(30_000L, 1_000L shl minOf(attempt, 5))
