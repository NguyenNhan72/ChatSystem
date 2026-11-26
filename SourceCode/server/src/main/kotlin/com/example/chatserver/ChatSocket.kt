package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.postgresql.util.PSQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val MAX_CONTENT_LENGTH = 99_999 // theo sach: noi dung text ngan hon 100.000 ky tu

/**
 * Phan quan ly trang thai cua chat server (sach: "Stateful Service"): luu session WebSocket nao thuoc ve user nao.
 * Mot user co the co nhieu session cung luc — moi thiet bi mot session (Hinh 12-13).
 * Don gian hoa: luu trong bo nho RAM nen phu hop voi 1 tien trinh server; neu co nhieu server se can message bus
 * hoac Redis pub/sub de server 1 chuyen tiep toi user dang ket noi o server 2 (Hinh 12-12 buoc 5a).
 */
class ConnectionRegistry {
    private val sessions = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()

    fun add(userId: Long, session: WebSocketSession) {
        sessions.compute(userId) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(session) } }
    }

    fun remove(userId: Long, session: WebSocketSession) {
        sessions.computeIfPresent(userId) { _, set -> set.remove(session); set.ifEmpty { null } }
    }

    fun isOnline(userId: Long): Boolean = sessions[userId]?.isNotEmpty() == true

    /** Day [event] toi tat ca session dang ket noi cua [userIds], bo qua [except] (session nguon tao ra su kien). */
    suspend fun send(userIds: Collection<Long>, event: ServerEvent, except: WebSocketSession? = null) {
        val text = Protocol.encode(event)
        for (session in userIds.flatMap { sessions[it].orEmpty() }) {
            if (session === except) continue
            // Mot socket bi loi khong duoc lam anh huong den viec gui tin toi cac socket khac; handler cua no se tu don dep.
            // Don gian hoa: gui tuan tu nen mot client cham co the lam cham luot fanout; chap nhan duoc o quy mo demo.
            runCatching { session.send(text) }
        }
    }
}

/** Endpoint WebSocket cua chat server. Xac thuc bang JWT truyen trong header Authorization cua HTTP upgrade request. */
fun Route.chatSocket(db: Database, registry: ConnectionRegistry, presence: PresenceService) {
    webSocket("/ws") {
        val me = call.userId()
        registry.add(me, this)
        try {
            send(Protocol.encode(ConnectedEvent(me)))
            presence.heartbeat(me) // Hinh 12-16: ket noi moi duoc coi la bat dau online
            // Trang thai ban dau cua nguoi khac lay qua REST (members[].online, /api/users) — sach: fetch khi can, khong fanout toan bo.
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val event = runCatching { Protocol.decodeClient(frame.readText()) }.getOrElse {
                    send(Protocol.encode(ErrorEvent(null, "BAD_EVENT", "Unrecognised or malformed event")))
                    continue
                }
                try {
                    when (event) {
                        is SendEvent -> handleSend(db, registry, me, event)
                        is DeliveredEvent -> handleCursor(db, registry, me, event.conversationId, deliveredSeq = event.seq, readSeq = 0)
                        is ReadEvent -> handleCursor(db, registry, me, event.conversationId, deliveredSeq = event.seq, readSeq = event.seq)
                        is ReactEvent -> handleReact(db, registry, me, event)
                        HeartbeatEvent -> presence.heartbeat(me)
                    }
                } catch (e: CancellationException) {
                    throw e // socket dang dong: de coroutine ket thuc tu nhien
                } catch (e: ApiException) {
                    send(Protocol.encode(ErrorEvent((event as? SendEvent)?.clientMsgId, e.code, e.message)))
                } catch (e: Exception) {
                    call.log("WebSocket event failed", e)
                    send(Protocol.encode(ErrorEvent((event as? SendEvent)?.clientMsgId, "INTERNAL", "Internal server error")))
                }
            }
        } finally {
            registry.remove(me, this)
            // Hinh 12-17: app dong ket noi voi ly do "logout" => offline ngay. Cac truong hop ngat khac (rot mang)
            // se cho timeout cua heartbeat de tranh nhap nhay trang thai.
            // Vong lap doc incoming co the ket thuc ngay truoc khi Ktor cap nhat closeReason nen ta cho toi da 1 giay.
            withContext(NonCancellable) {
                val reason = withTimeoutOrNull(1_000) { closeReason.await() }
                if (reason?.message == "logout") presence.logout(me)
            }
        }
    }
}

private fun ApplicationCall.log(msg: String, e: Exception) = application.log.error(msg, e)

/**
 * Luong gui tin 1-1 va nhom (Hinh 12-12 va 12-14 duoc dieu chinh):
 * kiem tra du lieu -> kiem tra quyen thanh vien -> cap seq + luu DB + chep vao inbox tung thanh vien trong 1 transaction
 * -> ack lai cho nguoi gui -> day tin toi cac thanh vien online (5a) / push FCM cho thanh vien offline (5b).
 * Thanh vien offline lay tin tu inbox cua ho (GET /api/sync) khi thiet bi online lai (Hinh 12-13).
 */
private suspend fun WebSocketSession.handleSend(db: Database, registry: ConnectionRegistry, me: Long, ev: SendEvent) {
    val clientMsgId = runCatching { UUID.fromString(ev.clientMsgId) }.getOrNull()
        ?: throw badRequest("INVALID_CLIENT_MSG_ID", "clientMsgId must be a UUID")
    if (ev.content.isBlank() || ev.content.length > MAX_CONTENT_LENGTH) {
        throw badRequest("INVALID_CONTENT", "Content must be 1-$MAX_CONTENT_LENGTH characters")
    }

    val (message, created, members) = store(db, me, ev, clientMsgId)
    if (message.senderId != me || message.conversationId != ev.conversationId) {
        throw badRequest("DUPLICATE_CLIENT_MSG_ID", "clientMsgId already used for another message")
    }

    send(Protocol.encode(AckEvent(ev.clientMsgId, message.conversationId, message.seq, message.createdAt)))
    // Neu la retry cua mot tin da duoc luu thi chi gui lai ack: nguoi nhan da nhan duoc roi (hoac se sync sau).
    if (created) {
        registry.send(members, MessageEvent(message), except = this)
        // Hinh 12-12 buoc 5b trong sach: nguoi nhan offline => gui Push Notification (FCM dong vai PN server)
        val offlineRecipients = members.filter { it != me && !registry.isOnline(it) }
        if (offlineRecipients.isNotEmpty()) {
            val (senderName, convTitle) = db.tx { c ->
                val sName = UserRepository.byId(c, me)?.displayName ?: "Tin nhắn mới"
                val groupName = c.query("SELECT name FROM conversations WHERE id = ? AND type = 'GROUP'", ev.conversationId) { it.getString(1) }.singleOrNull()
                Pair(sName, groupName ?: sName)
            }
            for (offlineId in offlineRecipients) {
                val fcmToken = db.tx { c -> DeviceTokenRepository.get(c, offlineId) }
                if (!fcmToken.isNullOrBlank()) {
                    val title = if (convTitle != senderName) "$convTitle - $senderName" else senderName
                    FcmService.sendPush(fcmToken, title, message.content, ev.conversationId, me)
                }
            }
        }
    }
}

private data class Stored(val message: MessageDto, val created: Boolean, val members: List<Long>)

/** Luu tin nhan tinh bat bien (idempotent): cung mot clientMsgId se luon tuong ung voi mot tin nhan duy nhat trong DB. */
private suspend fun store(db: Database, me: Long, ev: SendEvent, clientMsgId: UUID): Stored = try {
    db.tx { c ->
        requireMember(c, ev.conversationId, me)
        val members = ConversationRepository.memberIds(c, ev.conversationId)
        MessageRepository.byClientMsgId(c, clientMsgId)?.let { Stored(it, false, members) }
            ?: Stored(MessageRepository.insert(c, ev.conversationId, me, clientMsgId, ev.content), true, members)
    }
} catch (e: PSQLException) {
    // Hai ban sao gui cung luc bi tranh chap: ban thua bi vuong UNIQUE(client_msg_id) va bi rollback (bao gom ca seq).
    if (e.sqlState != UNIQUE_VIOLATION) throw e
    db.tx { c -> Stored(MessageRepository.byClientMsgId(c, clientMsgId)!!, false, emptyList()) }
}

/** delivered / read: tien con tro cua thanh vien va bao cho doi phuong (chi 1-1) de hien thi Da nhan / Da xem. */
private suspend fun WebSocketSession.handleCursor(
    db: Database, registry: ConnectionRegistry, me: Long, conversationId: Long, deliveredSeq: Long, readSeq: Long,
) {
    val (cursor, members) = db.tx { c ->
        val cursor = MessageRepository.moveCursor(c, conversationId, me, deliveredSeq, readSeq)
            ?: throw ApiException(io.ktor.http.HttpStatusCode.Forbidden, "NOT_A_MEMBER", "You are not a member of this conversation")
        cursor to ConversationRepository.memberIds(c, conversationId)
    }
    // Voi nhom chi cap nhat con tro (tinh so tin chua doc): khong phat receipt vi 100 thanh vien se tao ra qua nhieu event;
    // giao dien nhom chi can hien thi trang thai "Da gui".
    if (cursor.conversationType == "DIRECT") {
        registry.send(members, ReceiptEvent(conversationId, me, cursor.deliveredSeq, cursor.readSeq), except = this)
    }
}

/** Xu ly su kien tha emotion / reaction va phat toi tat ca thanh vien trong hoi thoai. */
private suspend fun handleReact(db: Database, registry: ConnectionRegistry, me: Long, ev: ReactEvent) {
    val members = db.tx { c ->
        requireMember(c, ev.conversationId, me)
        ConversationRepository.memberIds(c, ev.conversationId)
    }
    registry.send(members, ReactionEvent(ev.conversationId, ev.messageId, me, ev.reaction))
}
