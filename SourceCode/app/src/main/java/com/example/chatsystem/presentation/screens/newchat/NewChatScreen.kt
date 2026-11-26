package com.example.chatsystem.presentation.screens.newchat

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatsystem.presentation.components.Avatar
import com.example.chatsystem.presentation.components.CenteredMessage
import com.example.chatsystem.presentation.components.ErrorBanner
import com.example.chatsystem.presentation.components.MessengerIcons
import com.example.chatsystem.presentation.viewmodel.NewChatViewModel
import com.example.chatsystem.presentation.viewmodel.appViewModel
import com.example.chatsystem.ui.theme.MessengerBackground
import com.example.chatsystem.ui.theme.MessengerBlue
import com.example.chatsystem.ui.theme.MessengerDivider
import com.example.chatsystem.ui.theme.MessengerOnlineGreen
import com.example.chatsystem.ui.theme.MessengerSurface
import com.example.chatsystem.ui.theme.MessengerTextMuted
import com.example.chatsystem.ui.theme.MessengerTextPrimary
import com.example.chatsystem.ui.theme.MessengerTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onNewGroup: () -> Unit,
    onOpened: (conversationId: Long) -> Unit,
    vm: NewChatViewModel = appViewModel { NewChatViewModel(it.chatRepository) },
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MessengerBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MessengerBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Tin nhắn mới",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MessengerTextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MessengerBackground),
        ) {
            // O tim kiem nguoi nhan "Den: ..."
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MessengerSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Đến: ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MessengerTextSecondary,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (s.query.isEmpty()) {
                        Text(
                            text = "Nhập tên hoặc username...",
                            fontSize = 15.sp,
                            color = MessengerTextMuted,
                        )
                    }
                    BasicTextField(
                        value = s.query,
                        onValueChange = vm::onQuery,
                        singleLine = true,
                        cursorBrush = SolidColor(MessengerBlue),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = MessengerTextPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MessengerBlue,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (s.loading && s.results != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MessengerBlue)
            }
            if (s.error != null) ErrorBanner(s.error!!)

            val results = s.results

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // 1. The loi tat "Tao nhom moi"
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MessengerSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable(onClick = onNewGroup),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MessengerBlue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MessengerIcons.People,
                                    contentDescription = "New group",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Tạo nhóm mới",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MessengerTextPrimary,
                                )
                                Text(
                                    text = "Trò chuyện với tối đa 250 thành viên",
                                    fontSize = 12.sp,
                                    color = MessengerTextSecondary,
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Light,
                                color = MessengerTextMuted,
                            )
                        }
                    }
                }

                // 2. Tieu de phan: "DANH BA GOI Y"
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "DANH BẠ GỢI Ý",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = MessengerTextMuted,
                        )
                    }
                }

                // 3. Danh sach ket qua tim kiem nguoi dung
                when {
                    results == null -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MessengerBlue)
                            }
                        }
                    }

                    results.isEmpty() -> {
                        item {
                            CenteredMessage(
                                title = "Chưa tìm thấy người dùng",
                                body = "Nếu hệ thống mới khởi động lại, hãy chạy script tạo tài khoản demo hoặc gõ tìm kiếm.",
                            )
                        }
                    }

                    else -> {
                        items(results, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = s.openingUserId == null) { vm.open(user.id, onOpened) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(name = user.displayName, size = 46.dp, online = user.online)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MessengerTextPrimary,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (user.online) "• Đang hoạt động" else "• Không hoạt động",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (user.online) MessengerOnlineGreen else MessengerTextMuted,
                                        )
                                        Text(
                                            text = " · @${user.username}",
                                            fontSize = 12.sp,
                                            color = MessengerTextMuted,
                                        )
                                    }
                                }
                                if (s.openingUserId == user.id) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, color = MessengerBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                thickness = 0.5.dp,
                                color = MessengerDivider,
                            )
                        }
                    }
                }
            }
        }
    }
}
