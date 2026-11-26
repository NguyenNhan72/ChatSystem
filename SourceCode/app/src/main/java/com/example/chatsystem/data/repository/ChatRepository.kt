package com.example.chatsystem.data.repository

import android.util.Log
import com.example.chat.protocol.AckEvent
import com.example.chat.protocol.AddMembersRequest
import com.example.chat.protocol.ConnectedEvent
import com.example.chat.protocol.CreateDirectRequest
import com.example.chat.protocol.CreateGroupRequest
import com.example.chat.protocol.DeliveredEvent
import com.example.chat.protocol.ErrorEvent
import com.example.chat.protocol.MessageEvent
import com.example.chat.protocol.PresenceEvent
import com.example.chat.protocol.ReactEvent
import com.example.chat.protocol.ReactionEvent
import com.example.chat.protocol.ReadEvent
import com.example.chat.protocol.ReceiptEvent
import com.example.chat.protocol.SendEvent
import com.example.chat.protocol.ServerEvent
import com.example.chat.protocol.UserDto
import com.example.chatsystem.data.local.SessionStore
import com.example.chatsystem.data.local.database.AppDatabase
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.data.local.entity.MessageEntity
import com.example.chatsystem.data.remote.api.ChatApi
import com.example.chatsystem.data.remote.resultOf
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Nguon su that duy nhat (Single Source of Truth) = Room. UI chi lang nghe Room; phan hoi REST va WebSocket event deu ghi vao Room.
 *
 * Vong doi tin nhan tren thiet bi nay:
 *   gui:         Room insert SENDING (outbox) -> WS `send` -> `ack` => SENT (duoc cap seq) ; qua 10s khong co ack => FAILED
 *   nhan:        WS `message` (hoac trang sync) -> Room upsert theo clientMsgId -> WS `delivered` (hoac `read` neu dang mo cuoc tro chuyen)
 *   ket noi lai: server bao `connected` -> lay inbox sau cur_max_message_id (Hinh 12-13) -> gui lai toan bo outbox
 */
class ChatRepository(
    private val api: ChatApi,
    db: AppDatabase,
    private val socket: ChatSocketClient,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
) {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    val connection: StateFlow<ChatSocketClient.State> = socket.state

    @Volatile private var openConversationId: Long? = null
    private var syncJob: Job? = null
    private val myId: Long? get() = sessionStore.session.value?.userId
    private val _onlineUserIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        scope.launch {
            socket.events.collect { event ->
                try {
                    handle(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) { // mot event loi (vi du loi mang luc sync) khong duoc lam dung vong lap
                    Log.w(AppContainer.TAG, "Failed to handle $event", e)
                }
            }
        }
    }

    // ---- vong doi ket noi (dieu khien boi session trong AppContainer) ----

    fun connect(chatServerUrl: String) = socket.connect(chatServerUrl)

    fun disconnect() {
        socket.disconnect()
        openConversationId = null
    }

    // ---- cac truy van du lieu danh cho UI ----

    fun conversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun conversation(id: Long): Flow<ConversationEntity?> = conversationDao.observe(id)
    fun members(conversationId: Long): Flow<List<MemberEntity>> = conversationDao.observeMembers(conversationId)
    fun allMembers(): Flow<List<MemberEntity>> = conversationDao.observeAllMembers()
    fun messages(conversationId: Long): Flow<List<MessageEntity>> = messageDao.observe(conversationId)

    suspend fun refreshConversations(): Result<Unit> = resultOf {
        val remote = api.conversations()
        val members = remote.flatMap { it.members }
        // Ket qua REST moi nhat thay the trang thai cu cua nhung user nay (ke ca chuyen sang offline)
        _onlineUserIds.update { set -> set - members.map { it.user.id }.toSet() + members.filter { it.online }.map { it.user.id } }
        conversationDao.replaceAll(remote.map { it.toEntity() }, remote.flatMap { it.toMemberEntities() })
    }

    suspend fun searchUsers(query: String): Result<List<UserDto>> = resultOf {
        val users = api.searchUsers(query)
        _onlineUserIds.update { set -> set - users.map { it.id }.toSet() + users.filter { it.online }.map { it.id } }
        users
    }

    /** Lay hoac tao hoi thoai 1-1 voi [userId] va luu cache; tra ve id cua hoi thoai. */
    suspend fun openDirect(userId: Long): Result<Long> = resultOf {
        val dto = api.openDirect(CreateDirectRequest(userId))
        conversationDao.upsertOne(listOf(dto.toEntity()), dto.toMemberEntities())
        dto.id
    }

    /** Tao nhom moi (server tu dong them toi vao lam thanh vien) va luu cache; tra ve id cua nhom. */
    suspend fun createGroup(name: String, memberIds: List<Long>): Result<Long> = resultOf {
        val dto = api.createGroup(CreateGroupRequest(name, memberIds))
        conversationDao.upsertOne(listOf(dto.toEntity()), dto.toMemberEntities())
        dto.id
    }

    suspend fun addMembers(conversationId: Long, userIds: List<Long>): Result<Unit> = resultOf {
        val dto = api.addMembers(conversationId, AddMembersRequest(userIds))
        conversationDao.upsertOne(listOf(dto.toEntity()), dto.toMemberEntities())
    }

    // Mat ket noi => may nay khong con nhan presence nen khong biet ai online: an het cham xanh thay vi hien trang thai cu.
    // Ket noi lai => sync() goi refreshConversations() lay trang thai moi.
    fun conversationsWithSomeoneOnline(myUserId: Long): Flow<List<Long>> =
        combine(conversationDao.observeConversationsWithSomeoneOnline(myUserId), socket.state) { ids, state ->
            if (state == ChatSocketClient.State.CONNECTED) ids else emptyList()
        }

    fun onlineUserIds(): Flow<List<Long>> =
        combine(_onlineUserIds, conversationDao.observeOnlineUserIds(), socket.state) { memorySet, dbList, state ->
            if (state == ChatSocketClient.State.CONNECTED) (memorySet + dbList).toList() else emptyList()
        }

    // ---- man hinh chat ----

    /** Man hinh chat dang mo hoi thoai [id]: tin moi den se duoc danh dau da doc thay vi tinh vao so tin chua doc. */
    suspend fun openConversation(id: Long): Result<Unit> {
        openConversationId = id
        // Luon lay thong tin va thanh vien moi nhat de co day du display_name hien thi tren tin nhan
        val convDto = runCatching { api.conversation(id) }.getOrNull()
        if (convDto != null) {
            conversationDao.upsertOne(listOf(convDto.toEntity()), convDto.toMemberEntities())
        }
        val result = resultOf { fetchMissing(id, messageDao.maxSeq(id) ?: 0) }
        markRead(id)
        return result
    }

    suspend fun react(conversationId: Long, clientMsgId: String, emoji: String) {
        messageDao.updateReaction(clientMsgId, emoji)
        socket.send(ReactEvent(conversationId, clientMsgId, emoji))
    }

    fun closeConversation(id: Long) {
        if (openConversationId == id) openConversationId = null
    }

    /** Tai mot trang tin nhan cu hon; tra ve true khi tin nhan dau tien cua hoi thoai da duoc tai ve may. */
    suspend fun loadOlder(id: Long): Result<Boolean> = resultOf {
        val min = messageDao.minSeq(id) ?: return@resultOf true
        if (min <= 1) return@resultOf true
        val page = api.messages(id, beforeSeq = min, limit = PAGE)
        messageDao.upsert(page.map { it.toEntity() })
        page.size < PAGE
    }

    suspend fun send(conversationId: Long, text: String) {
        val me = myId ?: return
        val message = MessageEntity(UUID.randomUUID().toString(), conversationId, null, me, text, System.currentTimeMillis(), MessageEntity.SENDING)
        messageDao.upsert(listOf(message)) // luu vao outbox truoc: tin khong bi mat ke ca khi app bi kill truoc khi toi server
        transmit(message)
    }

    fun retry(clientMsgId: String) {
        scope.launch {
            messageDao.markRetrying(clientMsgId)
            messageDao.get(clientMsgId)?.takeIf { it.status == MessageEntity.SENDING }?.let { transmit(it) }
        }
    }

    private suspend fun markRead(id: Long) {
        conversationDao.clearUnread(id)
        val max = messageDao.maxSeq(id) ?: return
        socket.send(ReadEvent(id, max)) // neu dang offline thi lan mo chat / co tin moi ke tiep se gui lai
    }

    // ---- gui tin nhan ----

    /**
     * Giu nguyen clientMsgId trong moi lan thu lai = khoa chong trung lap (idempotency key): server chi luu 1 lan.
     * Offline: send() tra ve false, tin nhan nam lai trong outbox va se duoc gui sau khi co su kien connected.
     */
    private fun transmit(message: MessageEntity) {
        socket.send(SendEvent(message.clientMsgId, message.conversationId, message.content))
        scope.launch {
            delay(ACK_TIMEOUT_MS)
            messageDao.markFailed(message.clientMsgId) // khong anh huong neu ack da den truoc do
        }
    }

    private fun resendOutbox() {
        scope.launch {
            for (m in messageDao.pending()) {
                messageDao.markRetrying(m.clientMsgId)
                transmit(m)
            }
        }
    }

    // ---- xu ly cac event tu server ----

    private suspend fun handle(event: ServerEvent) {
        when (event) {
            is ConnectedEvent -> if (syncJob?.isActive != true) syncJob = scope.launch { sync() }
            is AckEvent -> {
                messageDao.markSent(event.clientMsgId, event.seq, event.createdAt)
                messageDao.get(event.clientMsgId)?.let { onStored(it, pushed = false) }
            }
            is MessageEvent -> {
                val m = event.message.toEntity()
                messageDao.upsert(listOf(m))
                onStored(m, pushed = true)
            }
            is PresenceEvent -> {
                _onlineUserIds.update { if (event.online) it + event.userId else it - event.userId }
                conversationDao.setPresence(event.userId, event.online, event.lastActiveAt)
            }
            is ReceiptEvent -> conversationDao.moveMemberCursor(event.conversationId, event.userId, event.deliveredSeq, event.readSeq)
            is ReactionEvent -> messageDao.updateReaction(event.messageId, event.reaction)
            is ErrorEvent -> {
                Log.w(AppContainer.TAG, "Server rejected event: ${event.code} ${event.message}")
                event.clientMsgId?.let { messageDao.markFailed(it) }
            }
        }
    }

    /** Chay sau moi lan (re)connect: lam moi danh sach hoi thoai, lay tin moi tu inbox, roi gui outbox. */
    private suspend fun sync() {
        if (refreshConversations().isFailure) return // socket se thu ket noi lai va chay sync tiep
        resultOf { pullInbox() }
        resendOutbox()
    }

    /**
     * Hinh 12-13 trong sach: tin moi cua thiet bi = tin trong inbox cua toi (Hinh 12-14/12-15) co inboxId > cur_max_message_id.
     * Moi thiet bi giu cur_max rieng nen dien thoai va laptop cua cung mot user tu dong bo doc lap.
     */
    private suspend fun pullInbox() {
        var curMax = sessionStore.curMaxInboxId()
        val received = mutableSetOf<Long>() // hoi thoai co tin moi tu nguoi khac => gui delivered
        while (true) {
            val page = api.sync(curMax, SYNC_PAGE)
            if (page.isEmpty()) break
            val messages = page.map { it.message.toEntity() }
            messageDao.upsert(messages)
            // unread da duoc tinh trong refreshConversations() => pushed = false de khong cong hai lan
            for (m in messages) onStored(m, pushed = false)
            messages.filter { it.senderId != myId }.mapTo(received) { it.conversationId }
            curMax = page.last().inboxId
            sessionStore.saveCurMaxInboxId(curMax) // luu sau khi da vao Room: app chet giua chung thi lan sau lay lai, upsert khong trung
            if (page.size < SYNC_PAGE) break
        }
        for (id in received) messageDao.maxSeq(id)?.let { acknowledge(id, it) }
    }

    /**
     * Lay tin cua mot hoi thoai tu lich su (khi mo chat, hoac bu khoang trong seq). Neu may chua co tin nao: chi tai trang moi nhat (tin cu se tai khi cuon).
     * Nguoc lai: tai moi tin sau max seq cua thiet bi, theo tung trang.
     */
    private suspend fun fetchMissing(id: Long, localMax: Long) {
        if (localMax == 0L) {
            messageDao.upsert(api.messages(id, limit = PAGE).map { it.toEntity() })
            return
        }
        var after = localMax
        while (true) {
            val page = api.messages(id, afterSeq = after, limit = SYNC_PAGE)
            messageDao.upsert(page.map { it.toEntity() })
            if (page.size < SYNC_PAGE) break
            after = page.last().seq
        }
    }

    /** Tin nhan da vao Room (nhan ack tin minh gui hoac push tu nguoi khac): cap nhat dong danh sach, bu tin bi thieu, gui xac nhan. */
    private suspend fun onStored(m: MessageEntity, pushed: Boolean) {
        val seq = m.seq ?: return
        val conv = conversationDao.get(m.conversationId)
        if (conv == null) { // hoi thoai moi ma nguoi khac vua bat dau voi toi
            refreshConversations()
        } else if (seq >= conv.lastSeq) {
            val countsAsUnread = pushed && m.senderId != myId && openConversationId != conv.id && seq > conv.lastSeq
            conversationDao.upsert(listOf(conv.copy(
                lastSeq = seq,
                lastMessageText = m.content,
                lastMessageSenderId = m.senderId,
                lastActivityAt = m.createdAt,
                unreadCount = conv.unreadCount + if (countsAsUnread) 1 else 0,
            )))
        }
        // Tin push co the den lech thu tu hoac sau khi bi lo mot tin: neu seq nhay cach quang thi lay bu phan thieu o giua.
        val previous = messageDao.maxSeqBelow(m.conversationId, seq)
        if (previous != null && previous < seq - 1) resultOf { fetchMissing(m.conversationId, previous) }
        if (pushed && m.senderId != myId) acknowledge(m.conversationId, seq)
    }

    private suspend fun acknowledge(conversationId: Long, seq: Long) {
        if (openConversationId == conversationId) markRead(conversationId) else socket.send(DeliveredEvent(conversationId, seq))
    }

    companion object {
        const val PAGE = 30
        const val SYNC_PAGE = 200
        const val ACK_TIMEOUT_MS = 10_000L
    }
}
