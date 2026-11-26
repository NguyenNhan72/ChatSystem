package com.example.chatsystem.presentation.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.chatsystem.presentation.viewmodel.GroupEditorViewModel
import com.example.chatsystem.presentation.viewmodel.MAX_GROUP_MEMBERS
import com.example.chatsystem.presentation.viewmodel.appViewModel
import com.example.chatsystem.ui.theme.MessengerBackground
import com.example.chatsystem.ui.theme.MessengerBlue
import com.example.chatsystem.ui.theme.MessengerDivider
import com.example.chatsystem.ui.theme.MessengerSurface
import com.example.chatsystem.ui.theme.MessengerTextMuted
import com.example.chatsystem.ui.theme.MessengerTextPrimary
import com.example.chatsystem.ui.theme.MessengerTextSecondary

/** Tao nhom moi (conversationId == null) hoac "them thanh vien" vao nhom hien co. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(
    conversationId: Long?,
    onBack: () -> Unit,
    onDone: (conversationId: Long) -> Unit,
    vm: GroupEditorViewModel = appViewModel { GroupEditorViewModel(it.chatRepository, conversationId) },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (s.creating) "Tạo nhóm mới" else "Thêm thành viên",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MessengerTextPrimary,
                    )
                    Text(
                        text = "Đã chọn ${s.selected.size} · tổng ${s.total}/$MAX_GROUP_MEMBERS thành viên",
                        fontSize = 12.sp,
                        color = if (s.total > MAX_GROUP_MEMBERS) MaterialTheme.colorScheme.error else MessengerTextSecondary,
                    )
                }
                if (s.saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MessengerBlue, modifier = Modifier.size(20.dp).padding(end = 12.dp))
                } else {
                    TextButton(
                        onClick = { vm.save(onDone) },
                        enabled = s.canSave,
                    ) {
                        Text(
                            text = if (s.creating) "Tạo" else "Thêm",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.canSave) MessengerBlue else MessengerTextMuted,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MessengerBackground),
        ) {
            // O nhap ten nhom
            if (s.creating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MessengerSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (s.name.isEmpty()) {
                            Text(
                                text = "Đặt tên nhóm (tùy chọn)...",
                                fontSize = 15.sp,
                                color = MessengerTextMuted,
                            )
                        }
                        BasicTextField(
                            value = s.name,
                            onValueChange = vm::onName,
                            singleLine = true,
                            cursorBrush = SolidColor(MessengerBlue),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                color = MessengerTextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // O tim kiem nguoi dung
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MessengerSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MessengerTextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (s.query.isEmpty()) {
                        Text(
                            text = "Tìm người để thêm...",
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
            }

            if (s.error != null) ErrorBanner(s.error!!)

            val results = s.results
            when {
                results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MessengerBlue)
                }
                results.isEmpty() -> CenteredMessage("Không còn ai để thêm")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { user ->
                        val checked = s.selected.any { it.id == user.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggle(user) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(name = user.displayName, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MessengerTextPrimary,
                                )
                                Text(
                                    text = "@${user.username}",
                                    fontSize = 12.sp,
                                    color = MessengerTextMuted,
                                )
                            }
                            // Nut chon hinh tron tich xanh phong cach Messenger
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) MessengerBlue else MessengerSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            thickness = 0.5.dp,
                            color = MessengerDivider,
                        )
                    }
                }
            }
        }
    }
}
