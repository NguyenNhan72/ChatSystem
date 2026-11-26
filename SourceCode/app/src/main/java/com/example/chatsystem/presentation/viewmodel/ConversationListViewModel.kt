package com.example.chatsystem.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.protocol.UserDto
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.remote.toUserMessage
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.data.repository.AuthRepository
import com.example.chatsystem.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationItem(
    val id: Long,
    val title: String,
    val preview: String,
    val time: Long,
    val unread: Int,
    val isGroup: Boolean,
    val online: Boolean = false, // co nguoi khac trong cuoc tro chuyen dang online
    val memberNames: List<String> = emptyList(),
)

data class ConversationListUiState(
    val items: List<ConversationItem>? = null, // null = Room chua tra ve du lieu
    val refreshing: Boolean = false,
    val error: String? = null,
    val connection: ChatSocketClient.State = ChatSocketClient.State.DISCONNECTED,
)

/** [senderName]: ten nguoi gui tin cuoi — nhom hien "Ten: noi dung" de biet ai gui. */
fun ConversationEntity.toItem(myUserId: Long?, senderName: String? = null) = ConversationItem(
    id = id,
    title = title,
    preview = when {
        lastMessageText == null -> "Chưa có tin nhắn"
        lastMessageSenderId == myUserId -> "Bạn: $lastMessageText"
        type == "GROUP" && senderName != null -> "$senderName: $lastMessageText"
        else -> lastMessageText
    },
    time = lastActivityAt,
    unread = unreadCount,
    isGroup = type == "GROUP",
)

class ConversationListViewModel(
    private val chatRepo: ChatRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val conversationData = combine(
        chatRepo.conversations(),
        chatRepo.conversationsWithSomeoneOnline(authRepo.session.value?.userId ?: -1),
        chatRepo.allMembers(),
    ) { list, onlineIds, members ->
        val me = authRepo.session.value?.userId
        val online = onlineIds.toSet()
        val membersByConv = members.groupBy { it.conversationId }
        list.map { conv ->
            val convMembers = membersByConv[conv.id].orEmpty()
            val otherMembers = convMembers.filter { it.userId != me }
            val memberNames = if (conv.type == "GROUP") {
                val names = (if (otherMembers.isNotEmpty()) otherMembers else convMembers).map { it.displayName }
                if (names.isNotEmpty()) names else conv.title.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            val senderName = convMembers.firstOrNull { it.userId == conv.lastMessageSenderId }?.displayName
            conv.toItem(me, senderName).copy(
                online = conv.type != "GROUP" && conv.id in online,
                memberNames = memberNames,
            )
        }
    }

    /** Room (du lieu cache) + trang thai refresh. Danh sach hien ngay tu cache, sau do cap nhat khi co ket qua mang. */
    val state: StateFlow<ConversationListUiState> =
        combine(
            conversationData,
            refreshing, error, chatRepo.connection,
        ) { items, r, e, connection ->
            ConversationListUiState(items, r, e, connection)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationListUiState(refreshing = true))

    private val _rawContacts = MutableStateFlow<List<UserDto>?>(null)

    val contacts: StateFlow<List<UserDto>?> =
        combine(_rawContacts, chatRepo.onlineUserIds()) { list, onlineIds ->
            val onlineSet = onlineIds.toSet()
            list?.filter { !it.username.startsWith("guest_") }?.map { user ->
                user.copy(online = user.id in onlineSet) // khong dung user.online cua lan tai REST: event presence offline phai ghi de duoc
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var refreshJob: Job? = null // khai bao truoc init vi cac khoi khoi tao chay tu tren xuong duoi

    init {
        refresh()
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            val r = chatRepo.searchUsers("")
            _rawContacts.value = r.getOrNull()
        }
    }

    fun openDirectChat(userId: Long, onOpened: (conversationId: Long) -> Unit) {
        viewModelScope.launch {
            chatRepo.openDirect(userId).onSuccess(onOpened)
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            error.value = chatRepo.refreshConversations().exceptionOrNull()?.toUserMessage()
            val r = chatRepo.searchUsers("")
            _rawContacts.value = r.getOrNull()
            refreshing.value = false
        }
    }

    fun switchAccount(username: String, displayName: String) {
        viewModelScope.launch {
            authRepo.switchAccount(username, displayName)
        }
    }
}
