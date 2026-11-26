package com.example.chatsystem.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatsystem.data.local.Session
import com.example.chatsystem.di.AppContainer
import com.example.chatsystem.data.remote.toUserMessage
import com.example.chatsystem.presentation.screens.chat.ChatScreen
import com.example.chatsystem.presentation.screens.conversations.ConversationListScreen
import com.example.chatsystem.presentation.screens.group.GroupEditorScreen
import com.example.chatsystem.presentation.screens.newchat.NewChatScreen

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val NEW_CHAT = "newChat"
    const val CHAT = "chat/{conversationId}"
    fun chat(id: Long) = "chat/$id"
    const val NEW_GROUP = "group/new"
    const val ADD_MEMBERS = "group/{conversationId}/add"
    fun addMembers(id: Long) = "group/$id/add"
}

/**
 * Khong con man hinh dang nhap: khi chua co session thi app tu dong dang nhap bang tai khoan khach cua may,
 * sau do chuyen thang toi man hinh danh sach chat. Neu token bi tu choi (401) se xoa session va quay ve day tu dong dang nhap lai.
 */
@Composable
fun AppRoot(container: AppContainer, initialConversationId: Long? = null) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    when (val s = session) {
        null -> SigningIn(container)
        else -> key(s.userId) { MainNavHost(s, initialConversationId) } // tai khoan moi => lam moi toan bo back stack va ViewModel
    }
}

/** Chi hien thi khi dang dang nhap, hoac khi khong ket noi duoc toi server (kem nut Thu lai). */
@Composable
private fun SigningIn(container: AppContainer) {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt) {
        error = null
        error = container.authRepository.signIn().exceptionOrNull()?.toUserMessage()
    }
    Scaffold(containerColor = com.example.chatsystem.ui.theme.MessengerBackground) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(com.example.chatsystem.ui.theme.MessengerBlue),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = com.example.chatsystem.presentation.components.MessengerIcons.ChatBubble,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            if (error == null) {
                CircularProgressIndicator(color = com.example.chatsystem.ui.theme.MessengerBlue)
                Text(
                    "Đang kết nối đến Chat…",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = com.example.chatsystem.ui.theme.MessengerTextSecondary,
                )
            } else {
                Text(
                    error!!,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = com.example.chatsystem.ui.theme.MessengerTextPrimary,
                )
                Button(
                    onClick = { attempt++ },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.example.chatsystem.ui.theme.MessengerBlue,
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Text("Thử lại")
                }
            }
        }
    }
}

@Composable
private fun MainNavHost(session: Session, initialConversationId: Long? = null) {
    val nav = rememberNavController()
    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null && initialConversationId > 0) {
            nav.navigate(Routes.chat(initialConversationId))
        }
    }
    NavHost(nav, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            ConversationListScreen(
                displayName = session.displayName,
                onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                onOpen = { id -> nav.navigate(Routes.chat(id)) },
            )
        }
        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onNewGroup = { nav.navigate(Routes.NEW_GROUP) },
                // thay the man chon nguoi bang man hinh chat, de khi bam Back se ve thang danh sach
                onOpened = { id -> nav.navigate(Routes.chat(id)) { popUpTo(Routes.NEW_CHAT) { inclusive = true } } },
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("conversationId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("conversationId")
            ChatScreen(conversationId = id, onBack = { nav.popBackStack() }, onAddMembers = { nav.navigate(Routes.addMembers(id)) })
        }
        composable(Routes.NEW_GROUP) {
            GroupEditorScreen(
                conversationId = null,
                onBack = { nav.popBackStack() },
                // mo nhom moi tao; khi bam Back se quay ve danh sach hoi thoai chu khong quay lai man chon nguoi
                onDone = { id -> nav.navigate(Routes.chat(id)) { popUpTo(Routes.CONVERSATIONS) } },
            )
        }
        composable(Routes.ADD_MEMBERS, arguments = listOf(navArgument("conversationId") { type = NavType.LongType })) { entry ->
            GroupEditorScreen(
                conversationId = entry.arguments!!.getLong("conversationId"),
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
    }
}
