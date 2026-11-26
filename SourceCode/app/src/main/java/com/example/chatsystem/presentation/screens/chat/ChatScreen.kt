package com.example.chatsystem.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatsystem.data.remote.websocket.ChatSocketClient
import com.example.chatsystem.presentation.components.Avatar
import com.example.chatsystem.presentation.components.CenteredMessage
import com.example.chatsystem.presentation.components.ErrorBanner
import com.example.chatsystem.presentation.components.OFFLINE_BANNER
import com.example.chatsystem.presentation.components.FloatingReactionBar
import com.example.chatsystem.presentation.components.MessengerIcons
import com.example.chatsystem.presentation.viewmodel.ChatViewModel
import com.example.chatsystem.presentation.viewmodel.DeliveryStatus
import com.example.chatsystem.presentation.viewmodel.MessageItem
import com.example.chatsystem.presentation.viewmodel.appViewModel
import com.example.chatsystem.ui.theme.BubbleIncoming
import com.example.chatsystem.ui.theme.BubbleIncomingText
import com.example.chatsystem.ui.theme.BubbleOutgoing
import com.example.chatsystem.ui.theme.BubbleOutgoingText
import com.example.chatsystem.ui.theme.MessengerBackground
import com.example.chatsystem.ui.theme.MessengerBlue
import com.example.chatsystem.ui.theme.MessengerDestructive
import com.example.chatsystem.ui.theme.MessengerOnlineGreen
import com.example.chatsystem.ui.theme.MessengerSurface
import com.example.chatsystem.ui.theme.MessengerTextMuted
import com.example.chatsystem.ui.theme.MessengerTextPrimary
import com.example.chatsystem.ui.theme.MessengerTextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
    onAddMembers: () -> Unit,
    vm: ChatViewModel = appViewModel { ChatViewModel(conversationId, it.chatRepository, it.sessionStore.session.value?.userId) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val newestFirst = s.items.asReversed() // reverseLayout: vi tri index 0 o duoi cung
    var reactingMessageId by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(conversationId) {
        onDispose {
            vm.close()
        }
    }

    // Cuon gan toi tin nhan cu nhat da tai -> goi phan trang tai them
    LaunchedEffect(listState, newestFirst.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (newestFirst.size >= 20 && last >= newestFirst.size - 5) vm.loadOlder() }
    }
    // Co tin nhan moi: cuon xuong duoi de hien thi
    LaunchedEffect(newestFirst.firstOrNull()?.id) {
        if (newestFirst.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Mat ket noi => khong biet doi phuong con online hay khong: khong hien cham xanh cu
    val isOnline = !s.isGroup && s.connection == ChatSocketClient.State.CONNECTED && (s.presence?.equals("Đang hoạt động", ignoreCase = true) == true ||
            s.presence?.contains("active now", ignoreCase = true) == true)

    Scaffold(
        containerColor = MessengerBackground,
        topBar = {
            MessengerChatTopBar(
                title = s.title,
                isGroup = s.isGroup,
                memberNames = s.memberNames,
                presence = s.presence,
                connection = s.connection,
                isOnline = isOnline,
                onBack = onBack,
                onAddMembers = onAddMembers,
            )
        },
        bottomBar = {
            MessengerInputBar(
                draft = draft,
                onDraftChange = { if (it.length <= ChatViewModel.MAX_LENGTH) draft = it },
                onSend = {
                    if (draft.isNotBlank()) {
                        vm.send(draft.trim())
                        draft = ""
                    }
                },
                onQuickThumbUp = {
                    vm.send("👍")
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MessengerBackground),
        ) {
            if (s.error != null) ErrorBanner("${s.error} — đang hiển thị tin đã lưu")
            if (s.connection == ChatSocketClient.State.DISCONNECTED) ErrorBanner(OFFLINE_BANNER)

            if (newestFirst.isEmpty()) {
                CenteredMessage(
                    title = "Chưa có tin nhắn",
                    body = "Hãy vẫy tay hoặc gửi lời chào đầu tiên!",
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    ) {
                        items(newestFirst, key = { it.id }) { item ->
                            val isReacting = reactingMessageId == item.id
                            Column(Modifier.fillMaxWidth()) {
                                if (isReacting) {
                                    FloatingReactionBar(
                                        onReactionClick = { emoji ->
                                            reactingMessageId?.let { msgId ->
                                                vm.react(msgId, emoji)
                                            }
                                            reactingMessageId = null
                                        },
                                        modifier = Modifier
                                            .align(if (item.mine) Alignment.End else Alignment.Start)
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                                MessageBubble(
                                    item = item,
                                    conversationTitle = s.title,
                                    onBubbleClick = {
                                        reactingMessageId = if (isReacting) null else item.id
                                    },
                                    onRetry = { vm.retry(item.id) },
                                )
                            }
                        }

                        if (s.loadingOlder) item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp, color = MessengerBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thanh tieu de tren cung phong cach Messenger:
 * Nut Back, Avatar + cham online, Tieu de, trang thai hoat dong,
 * Nut goi thoai, goi video, va nut them thanh vien / thong tin nhom.
 */
@Composable
private fun MessengerChatTopBar(
    title: String,
    isGroup: Boolean,
    memberNames: List<String> = emptyList(),
    presence: String?,
    connection: ChatSocketClient.State,
    isOnline: Boolean,
    onBack: () -> Unit,
    onAddMembers: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MessengerBackground)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nut mui ten quay lai (Back)
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MessengerBlue,
                modifier = Modifier.size(24.dp),
            )
        }

        // Avatar nguoi lien he / nhom
        Avatar(
            name = title,
            size = 38.dp,
            online = if (isGroup) false else isOnline,
            groupMemberNames = if (isGroup) memberNames else emptyList(),
        )

        Spacer(Modifier.width(10.dp))

        // Ten va trang thai hoat dong
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MessengerTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                connection == ChatSocketClient.State.CONNECTING -> "Đang kết nối…"
                connection == ChatSocketClient.State.DISCONNECTED -> "Mất kết nối"
                isGroup -> presence ?: "Nhóm"
                isOnline -> "Đang hoạt động"
                presence != null -> presence
                else -> "Không hoạt động"
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isOnline) MessengerOnlineGreen else MessengerTextMuted,
                maxLines = 1,
            )
        }

        // Nut goi thoai (Audio call)
        IconButton(onClick = {}) {
            Icon(
                imageVector = MessengerIcons.Phone,
                contentDescription = "Audio call",
                tint = MessengerBlue,
                modifier = Modifier.size(22.dp),
            )
        }

        // Nut goi video (Video call)
        IconButton(onClick = {}) {
            Icon(
                imageVector = MessengerIcons.VideoCall,
                contentDescription = "Video call",
                tint = MessengerBlue,
                modifier = Modifier.size(22.dp),
            )
        }

        // Nut them thanh vien / thong tin nhom
        IconButton(onClick = if (isGroup) onAddMembers else { {} }) {
            Icon(
                imageVector = if (isGroup) MessengerIcons.GroupAdd else MessengerIcons.Info,
                contentDescription = "Information",
                tint = MessengerBlue,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Bong bong tin nhan phong cach Messenger:
 * - Tin den: avatar ben trai (28dp), nen xam nhe (#F0F2F5), chu den (#050505)
 * - Tin di: nen xanh Messenger (#0084FF), chu trang (#FFFFFF)
 * - Trang thai Da xem: chu "Seen" + avatar nho (14dp)
 */
@Composable
private fun MessageBubble(
    item: MessageItem,
    conversationTitle: String,
    onBubbleClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = item.status == DeliveryStatus.FAILED

    Column(
        horizontalAlignment = if (item.mine) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        // Nhan ten nguoi gui neu la trong nhom
        if (item.senderName != null) {
            Text(
                text = item.senderName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MessengerTextSecondary,
                modifier = Modifier.padding(start = 38.dp, bottom = 2.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!item.mine) {
                // Avatar nguoi gui tin nhan den (28dp)
                Avatar(name = item.senderName ?: conversationTitle, size = 28.dp)
                Spacer(Modifier.width(6.dp))
            }

            // Bong bong tin nhan (Bubble) kem huy hieu emotion ghim dinh o goc
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.padding(bottom = if (item.reaction != null) 6.dp else 0.dp),
            ) {
                Surface(
                    shape = if (item.mine) {
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    } else {
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                    },
                    color = when {
                        failed -> Color(0xFFFFEBEA)
                        item.mine -> BubbleOutgoing
                        else -> BubbleIncoming
                    },
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { if (failed) onRetry() else onBubbleClick() },
                ) {
                    Text(
                        text = item.text,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = when {
                            failed -> MessengerDestructive
                            item.mine -> BubbleOutgoingText
                            else -> BubbleIncomingText
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }

                // Huy hieu emotion / reaction ghim dinh vao goc duoi bong bong giong Messenger
                if (item.reaction != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E6EB)),
                        modifier = Modifier
                            .offset(x = 6.dp, y = 8.dp)
                            .clickable { onBubbleClick() },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = item.reaction,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        // Trang thai & xac nhan da xem phia duoi tin nhan
        if (item.mine) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = 4.dp),
            ) {
                when (item.status) {
                    DeliveryStatus.READ -> {
                        // Hien thi "Seen" kem avatar nho (14dp)
                        Text(
                            text = "Seen",
                            fontSize = 11.sp,
                            color = MessengerTextMuted,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Avatar(name = conversationTitle, size = 14.dp)
                    }
                    DeliveryStatus.DELIVERED -> {
                        Text(
                            text = "Đã nhận",
                            fontSize = 11.sp,
                            color = MessengerTextMuted,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MessengerTextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    DeliveryStatus.SENT -> {
                        Text(
                            text = "Đã gửi",
                            fontSize = 11.sp,
                            color = MessengerTextMuted,
                        )
                    }
                    DeliveryStatus.SENDING -> {
                        Text(
                            text = "Đang gửi…",
                            fontSize = 11.sp,
                            color = MessengerTextMuted,
                        )
                    }
                    DeliveryStatus.FAILED -> {
                        Text(
                            text = "Chưa gửi được · chạm để thử lại",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MessengerDestructive,
                            modifier = Modifier.clickable(onClick = onRetry),
                        )
                    }
                    null -> {}
                }
            }
        }
    }
}

/**
 * Thanh nhap tin nhan phia duoi phong cach Messenger:
 * Cac nut ben trai: (+) Them, Camera, Anh, Mic mau #0084FF
 * O nhap text: Bo tron, mau #F0F2F5, placeholder "Aa", icon mat cuoi ben trong
 * Nut ben phai: Bieu tuong like 👍 khi rong, nut Gui khi co noi dung!
 */
@Composable
private fun MessengerInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickThumbUp: () -> Unit,
) {
    Surface(
        color = MessengerBackground,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cac icon thao tac ben trai mau xanh Messenger
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "More actions",
                    tint = MessengerBlue,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = MessengerIcons.Camera,
                    contentDescription = "Camera",
                    tint = MessengerBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = MessengerIcons.Photo,
                    contentDescription = "Photos",
                    tint = MessengerBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = MessengerIcons.Mic,
                    contentDescription = "Voice note",
                    tint = MessengerBlue,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            // O nhap van ban bo tron (Pill Input Field)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MessengerSurface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Aa",
                            fontSize = 15.sp,
                            color = MessengerTextMuted,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        cursorBrush = SolidColor(MessengerBlue),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = MessengerTextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Icon mat cuoi ben trong o nhap
                Icon(
                    imageVector = MessengerIcons.Smile,
                    contentDescription = "Emoji",
                    tint = MessengerBlue,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {},
                )
            }

            Spacer(Modifier.width(6.dp))

            // Nut ben phai: nut like neu rong, nut Gui neu co noi dung
            if (draft.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MessengerBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = onQuickThumbUp,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = MessengerIcons.ThumbUp,
                        contentDescription = "Like",
                        tint = MessengerBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
