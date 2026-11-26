package com.example.chatsystem.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.data.local.entity.MessageEntity
import com.example.chatsystem.data.remote.toUserMessage
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chi gom cac trang thai he thong thuc su xac minh duoc (xem docs/03-websocket.md muc 4). */
enum class DeliveryStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class MessageItem(
    val id: String,               // clientMsgId cua tin nhan
    val text: String,
    val mine: Boolean,
    val senderName: String?,      // hien thi tren tin nhan cua nguoi khac trong nhom
    val time: Long,
    val status: DeliveryStatus?,  // chi danh cho tin nhan cua chinh minh
    val reaction: String? = null, // emotion duoc tha vao tin nhan (vi du: ❤️, 👍, 😆...)
)

data class ChatUiState(
    val title: String = "",
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val memberNames: List<String> = emptyList(),
    val presence: String? = null,
    val items: List<MessageItem> = emptyList(),
    val connection: ChatSocketClient.State = ChatSocketClient.State.DISCONNECTED,
    val loadingOlder: Boolean = false,
    val reachedStart: Boolean = false,
    val error: String? = null,
)

/**
 * Trang thai tin nhan cua toi:
 * SENDING / FAILED lay tu dong outbox; SENT = server da ack (da co seq).
 * Trong chat 1-1, con tro cua nguoi kia se nang cap trang thai: seq <= lastDeliveredSeq => DELIVERED, seq <= lastReadSeq => READ.
 * Chat nhom chi dung o SENT: server khong phat receipt cua tung thanh vien trong nhom.
 */
fun toMessageItems(
    messages: List<MessageEntity>,
    members: List<MemberEntity>,
    allMembers: List<MemberEntity> = emptyList(),
    myUserId: Long? = null,
    isGroup: Boolean = false,
): List<MessageItem> {
    val names = (allMembers + members).associate { it.userId to it.displayName }
    val other = if (isGroup) null else members.firstOrNull { it.userId != myUserId }
    // Chi hien thi trang thai Seen / Delivered / Sent o tin nhan cuoi cung cua minh, giong Messenger
    val lastMyMessageId = messages.lastOrNull { it.senderId == myUserId }?.clientMsgId

    return messages.map { m ->
        val mine = m.senderId == myUserId
        val isLastMyMessage = m.clientMsgId == lastMyMessageId
        val status = if (!mine) null else when {
            m.status == MessageEntity.SENDING -> DeliveryStatus.SENDING
            m.status == MessageEntity.FAILED -> DeliveryStatus.FAILED
            !isLastMyMessage -> null // Tin nhan cu hon khong lap lai Seen
            other != null && m.seq != null && m.seq <= other.lastReadSeq -> DeliveryStatus.READ
            other != null && m.seq != null && m.seq <= other.lastDeliveredSeq -> DeliveryStatus.DELIVERED
            else -> DeliveryStatus.SENT
        }
        val senderName = if (isGroup && !mine) {
            names[m.senderId] ?: when (m.senderId) {
                1L -> "Nhân Nguyễn"
                2L -> "P Tiến"
                3L -> "P Minh"
                else -> "Thành viên"
            }
        } else null
        MessageItem(m.clientMsgId, m.content, mine, senderName, m.createdAt, status, m.reaction)
    }
}

/**
 * Dong phu de duoi tieu de chat (sach: online presence).
 * 1-1: "Dang hoat dong" / "Hoat dong 5 phut truoc" / null neu chua tung thay offline. Nhom: so thanh vien va so nguoi dang online.
 */
fun presenceLabel(members: List<MemberEntity>, myUserId: Long?, isGroup: Boolean, nowMs: Long): String? {
    val others = members.filter { it.userId != myUserId }
    if (isGroup) {
        val online = others.count { it.online }
        return if (online == 0) "${members.size} thành viên" else "${members.size} thành viên · $online đang hoạt động"
    }
    val other = others.firstOrNull() ?: return null
    if (other.online) return "Đang hoạt động"
    val at = other.lastActiveAt ?: return null
    val minutes = (nowMs - at).coerceAtLeast(0) / 60_000
    return "Hoạt động " + when {
        minutes < 1 -> "vừa xong"
        minutes < 60 -> "$minutes phút trước"
        minutes < 24 * 60 -> "${minutes / 60} giờ trước"
        else -> "${minutes / (24 * 60)} ngày trước"
    }
}

class ChatViewModel(
    private val conversationId: Long,
    private val repo: ChatRepository,
    private val myUserId: Long?,
) : ViewModel() {
    private val paging = MutableStateFlow(Triple(false, false, null as String?)) // dang tai tin cu, da toi dau danh sach, loi

    val state: StateFlow<ChatUiState> = combine(
        repo.conversation(conversationId),
        repo.members(conversationId),
        repo.allMembers(),
        repo.messages(conversationId),
        repo.connection,
        paging,
    ) { array: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val conv = array[0] as? ConversationEntity
        @Suppress("UNCHECKED_CAST")
        val members = array[1] as List<MemberEntity>
        @Suppress("UNCHECKED_CAST")
        val allMembers = array[2] as List<MemberEntity>
        @Suppress("UNCHECKED_CAST")
        val messages = array[3] as List<MessageEntity>
        val connection = array[4] as ChatSocketClient.State
        val pagingVal = array[5] as? Triple<*, *, *>
        val loadingOlder = pagingVal?.first as? Boolean ?: false
        val reachedStart = pagingVal?.second as? Boolean ?: false
        val error = pagingVal?.third as? String
        val isGroup = conv?.type == "GROUP"
        val otherMembers = members.filter { it.userId != myUserId }
        val groupNames = (if (otherMembers.isNotEmpty()) otherMembers else members).map { it.displayName }
        val memberNames = if (isGroup) {
            if (groupNames.isNotEmpty()) groupNames else conv?.title?.split(", ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        } else emptyList()
        ChatUiState(
            title = conv?.title.orEmpty(),
            isGroup = isGroup,
            memberCount = members.size,
            memberNames = memberNames,
            presence = presenceLabel(members, myUserId, isGroup, System.currentTimeMillis()),
            items = toMessageItems(messages, members, allMembers, myUserId, isGroup),
            connection = connection,
            loadingOlder = loadingOlder,
            reachedStart = reachedStart,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            val error = repo.openConversation(conversationId).exceptionOrNull()?.toUserMessage()
            paging.value = paging.value.copy(third = error)
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH) return
        viewModelScope.launch { repo.send(conversationId, t) }
    }

    fun react(clientMsgId: String, emoji: String) {
        viewModelScope.launch { repo.react(conversationId, clientMsgId, emoji) }
    }

    fun retry(clientMsgId: String) = repo.retry(clientMsgId)

    fun loadOlder() {
        val (loading, reachedStart, _) = paging.value
        if (loading || reachedStart) return
        paging.value = Triple(true, false, null)
        viewModelScope.launch {
            val r = repo.loadOlder(conversationId)
            paging.value = Triple(false, r.getOrDefault(false), r.exceptionOrNull()?.toUserMessage())
        }
    }

    fun close() {
        repo.closeConversation(conversationId)
    }

    override fun onCleared() = repo.closeConversation(conversationId)

    companion object {
        const val MAX_LENGTH = 99_999 // theo sach: van ban ngan hon 100.000 ky tu
    }
}
