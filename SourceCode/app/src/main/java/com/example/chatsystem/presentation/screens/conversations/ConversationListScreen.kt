package com.example.chatsystem.presentation.screens.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chat.protocol.UserDto
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.presentation.components.ActiveUserItem
import com.example.chatsystem.presentation.components.Avatar
import com.example.chatsystem.presentation.components.CenteredMessage
import com.example.chatsystem.presentation.components.ErrorBanner
import com.example.chatsystem.presentation.components.OFFLINE_BANNER
import com.example.chatsystem.presentation.components.FloatingReactionBar
import com.example.chatsystem.presentation.components.MessengerActiveTray
import com.example.chatsystem.presentation.components.MessengerBottomNav
import com.example.chatsystem.presentation.components.MessengerIcons
import com.example.chatsystem.presentation.components.MessengerSearchBar
import com.example.chatsystem.presentation.components.formatListTime
import com.example.chatsystem.presentation.viewmodel.ConversationItem
import com.example.chatsystem.presentation.viewmodel.ConversationListViewModel
import com.example.chatsystem.presentation.viewmodel.appViewModel
import com.example.chatsystem.ui.theme.MessengerBackground
import com.example.chatsystem.ui.theme.MessengerBlue
import com.example.chatsystem.ui.theme.MessengerDestructive
import com.example.chatsystem.ui.theme.MessengerSurface
import com.example.chatsystem.ui.theme.MessengerTextMuted
import com.example.chatsystem.ui.theme.MessengerTextPrimary
import com.example.chatsystem.ui.theme.MessengerTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    displayName: String,
    onNewChat: () -> Unit,
    onOpen: (conversationId: Long) -> Unit,
    vm: ConversationListViewModel = appViewModel { ConversationListViewModel(it.chatRepository, it.authRepository) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var peopleSearchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var contextConversation by remember { mutableStateOf<ConversationItem?>(null) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isConnected = s.connection == ChatSocketClient.State.CONNECTED
    val totalUnread = s.items?.sumOf { it.unread } ?: 0

    // Loc chi lay cac cuoc tro chuyen thuc te cua nguoi dung
    val filteredItems = remember(s.items, searchQuery) {
        val items = s.items ?: return@remember null
        if (searchQuery.isBlank()) items
        else items.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.preview.contains(searchQuery, ignoreCase = true)
        }
    }

    // Trich xuat danh sach lien he online (bao gom ca hoi thoai da co va ban be online chua co hoi thoai)
    val activeContacts = remember(s.items, contacts) {
        val directItems = s.items.orEmpty()
            .filter { !it.isGroup && it.online }
            .map { ActiveUserItem(id = it.id, name = it.title, online = true) }
        val directNames = directItems.map { it.name.trim().lowercase() }.toSet()
        val otherOnlineFriends = contacts.orEmpty()
            .filter { it.online && it.displayName.trim().lowercase() !in directNames && !it.displayName.equals(displayName, ignoreCase = true) }
            .map { ActiveUserItem(id = -it.id, name = it.displayName, online = true) }
        directItems + otherOnlineFriends
    }

    Scaffold(
        containerColor = MessengerBackground,
        bottomBar = {
            MessengerBottomNav(
                selectedTab = selectedTab,
                unreadCount = totalUnread,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = s.refreshing && (s.items.orEmpty().isNotEmpty() || contacts.orEmpty().isNotEmpty()),
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MessengerBackground),
            ) {
                // Header tren cung: [Avatar] Chats/People/Discover        [Camera] [Action]
                MessengerHeader(
                    displayName = displayName,
                    isConnected = isConnected,
                    title = when (selectedTab) {
                        1 -> "People"
                        2 -> "Discover"
                        else -> "Chats"
                    },
                    actionIcon = if (selectedTab == 1) MessengerIcons.GroupAdd else MessengerIcons.Compose,
                    onAvatarClick = { showAccountSwitcher = true },
                    onNewChat = onNewChat,
                )

                if (s.error != null && s.items.isNullOrEmpty() && contacts.isNullOrEmpty()) {
                    ErrorBanner(s.error!!)
                } else if (s.connection == ChatSocketClient.State.DISCONNECTED) {
                    ErrorBanner(OFFLINE_BANNER)
                }

                when (selectedTab) {
                    0 -> ChatsTabContent(
                        items = filteredItems,
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        refreshing = s.refreshing,
                        userDisplayName = displayName,
                        activeContacts = activeContacts,
                        onOpen = { id ->
                            if (id < 0) vm.openDirectChat(-id, onOpen) else onOpen(id)
                        },
                        onLongClick = { contextConversation = it },
                    )
                    1 -> PeopleScreenContent(
                        contacts = contacts,
                        searchQuery = peopleSearchQuery,
                        onQueryChange = { peopleSearchQuery = it },
                        onOpenContact = { userId -> vm.openDirectChat(userId, onOpen) },
                        refreshing = s.refreshing,
                    )
                    else -> DiscoverScreenContent()
                }
            }
        }
    }

    // Bottom sheet thao tac nhanh cho cuoc tro chuyen
    contextConversation?.let { conversation ->
        ModalBottomSheet(
            onDismissRequest = { contextConversation = null },
            sheetState = sheetState,
            containerColor = MessengerBackground,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            ChatContextMenuSheet(
                conversation = conversation,
                onReactionClick = { emoji ->
                    contextConversation = null
                },
                onOpen = {
                    val id = conversation.id
                    contextConversation = null
                    onOpen(id)
                },
                onDismiss = { contextConversation = null },
            )
        }
    }

    // Dialog chuyen doi nhanh giua 3 tai khoan demo: Nhan Nguyen, P Tien, P Minh
    if (showAccountSwitcher) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAccountSwitcher = false },
            title = {
                Text(
                    text = "Chuyển tài khoản",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MessengerTextPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Chọn tài khoản để trải nghiệm:",
                        fontSize = 13.sp,
                        color = MessengerTextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Pair("nhan", "Nhân Nguyễn"),
                        Pair("ptien", "P Tiến"),
                        Pair("pminh", "P Minh"),
                    ).forEach { (username, name) ->
                        val isCurrent = displayName.equals(name, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MessengerBlue.copy(alpha = 0.1f) else Color(0xFFF0F2F5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAccountSwitcher = false
                                    vm.switchAccount(username, name)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Avatar(name = name, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = if (isCurrent) MessengerBlue else MessengerTextPrimary,
                                    )
                                    Text(
                                        text = "@$username",
                                        fontSize = 12.sp,
                                        color = MessengerTextSecondary,
                                    )
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "Hiện tại",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MessengerBlue,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showAccountSwitcher = false }) {
                    Text("Đóng", color = MessengerBlue)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun MessengerHeader(
    displayName: String,
    isConnected: Boolean,
    title: String = "Chats",
    actionIcon: ImageVector = MessengerIcons.Compose,
    onAvatarClick: () -> Unit = {},
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable(onClick = onAvatarClick)) {
            Avatar(name = displayName, size = 40.dp, online = isConnected)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MessengerTextPrimary,
            modifier = Modifier.weight(1f),
        )
        HeaderCircleButton(
            icon = MessengerIcons.Camera,
            contentDescription = "Camera",
            onClick = {},
        )
        Spacer(Modifier.width(8.dp))
        HeaderCircleButton(
            icon = actionIcon,
            contentDescription = "Action",
            onClick = onNewChat,
        )
    }
}

@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MessengerSurface),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MessengerTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChatsTabContent(
    items: List<ConversationItem>?,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    refreshing: Boolean,
    userDisplayName: String,
    activeContacts: List<ActiveUserItem>,
    onOpen: (Long) -> Unit,
    onLongClick: (ConversationItem) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Thanh tim kiem: Tim kiem
        MessengerSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        when {
            items == null || (items.isEmpty() && refreshing) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MessengerBlue)
                }
            }

            items.isEmpty() && searchQuery.isNotEmpty() -> {
                CenteredMessage("Không tìm thấy cuộc trò chuyện", "Không có tin nhắn nào khớp với \"$searchQuery\"")
            }

            items.isEmpty() -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        MessengerActiveTray(
                            userDisplayName = userDisplayName,
                            activeContacts = emptyList(),
                            onUserClick = onOpen,
                        )
                    }
                    item {
                        CenteredMessage(
                            title = "Chưa có cuộc trò chuyện",
                            body = "Bấm vào biểu tượng soạn tin nhắn ở góc trên bên phải để bắt đầu trò chuyện.",
                        )
                    }
                }
            }

            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    // Hang trang thai hoat dong ("Tin cua ban" + lien he dang online)
                    item {
                        MessengerActiveTray(
                            userDisplayName = userDisplayName,
                            activeContacts = activeContacts,
                            onUserClick = onOpen,
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // Chi hien thi cac dong hoi thoai thuc te
                    items(items, key = { it.id }) { item ->
                        ConversationRow(
                            item = item,
                            onClick = { onOpen(item.id) },
                            onLongClick = { onLongClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleScreenContent(
    contacts: List<UserDto>?,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    refreshing: Boolean,
) {
    val filtered = remember(contacts, searchQuery) {
        val list = contacts ?: return@remember null
        if (searchQuery.isBlank()) list
        else list.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Thanh tim kiem ban be
        MessengerSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            placeholder = "Tìm kiếm bạn bè...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        when {
            filtered == null || (filtered.isEmpty() && refreshing) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MessengerBlue)
                }
            }
            filtered.isEmpty() && searchQuery.isNotEmpty() -> {
                CenteredMessage("Không tìm thấy bạn bè", "Không có ai khớp với \"$searchQuery\"")
            }
            filtered.isEmpty() -> {
                CenteredMessage("Chưa có bạn bè", "Hệ thống chưa có người dùng nào khác.")
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "TẤT CẢ BẠN BÈ (${filtered.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MessengerTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }

                    items(filtered, key = { it.id }) { user ->
                        FriendContactRow(
                            user = user,
                            onClick = { onOpenContact(user.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendContactRow(
    user: UserDto,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = user.displayName, size = 52.dp, online = user.online)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MessengerTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (user.online) "@${user.username} · Đang hoạt động" else "@${user.username} · Không hoạt động",
                fontSize = 13.sp,
                color = MessengerTextSecondary,
            )
        }
        HeaderCircleButton(
            icon = MessengerIcons.ChatBubble,
            contentDescription = "Chat",
            onClick = onClick,
        )
    }
}

@Composable
private fun DiscoverScreenContent() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MessengerIcons.Discover,
            contentDescription = null,
            tint = MessengerBlue,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Khám phá",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MessengerTextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Khám phá các kênh và cộng đồng trò chuyện mới.",
            fontSize = 14.sp,
            color = MessengerTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Dong cuoc tro chuyen trong danh sach:
 * - Avatar tron (54dp) voi chu cai dau va cham online
 * - Dong 1: Ten lien he (16sp, dam) + Thoi gian (12sp, vi du "09:34")
 * - Dong 2: Noi dung xem truoc cua tin nhan (14sp)
 * - Phia phai: Cham xanh neu chua doc, bieu tuong tich xam neu da nhan/xem
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isUnread = item.unread > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            name = item.title,
            size = 54.dp,
            online = if (item.isGroup) false else item.online,
            groupMemberNames = if (item.isGroup) item.memberNames else emptyList(),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // Dong 1: Ten lien he & Thoi gian
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MessengerTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatListTime(item.time),
                    fontSize = 12.sp,
                    color = if (isUnread) MessengerBlue else MessengerTextMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            // Dong 2: Noi dung tin nhan xem truoc
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.preview,
                    fontSize = 14.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    color = if (isUnread) MessengerTextPrimary else MessengerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Chi bao phia ben phai: Cham xanh neu chua doc, tich xam neu da xem
        if (isUnread) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MessengerBlue),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Delivered",
                tint = MessengerTextMuted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(14.dp),
            )
        }
    }
}

/**
 * Menu thao tac nhanh khi nhan giu cuoc tro chuyen.
 */
@Composable
private fun ChatContextMenuSheet(
    conversation: ConversationItem,
    onReactionClick: (String) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Thanh tha cam xuc noi
        FloatingReactionBar(
            onReactionClick = onReactionClick,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // The hoi thoai dang duoc chon
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MessengerSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    name = conversation.title,
                    size = 46.dp,
                    online = if (conversation.isGroup) false else conversation.online,
                    groupMemberNames = if (conversation.isGroup) conversation.memberNames else emptyList(),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = conversation.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MessengerTextPrimary,
                    )
                    Text(
                        text = conversation.preview,
                        fontSize = 13.sp,
                        color = MessengerTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Cac muc menu thao tac
        ContextMenuItem(
            icon = Icons.Default.Email,
            text = "Mark as read",
            onClick = onDismiss,
        )
        ContextMenuItem(
            icon = Icons.Default.Notifications,
            text = "Mute notifications",
            onClick = onDismiss,
        )
        ContextMenuItem(
            icon = MessengerIcons.ChatBubble,
            text = "Open conversation",
            onClick = onOpen,
        )
        ContextMenuItem(
            icon = Icons.Default.Delete,
            text = "Delete conversation",
            isDestructive = true,
            onClick = onDismiss,
        )

        Spacer(Modifier.height(16.dp))

        // Nut xong (Dong)
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MessengerSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "Done",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MessengerBlue,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (isDestructive) MessengerDestructive else MessengerTextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
