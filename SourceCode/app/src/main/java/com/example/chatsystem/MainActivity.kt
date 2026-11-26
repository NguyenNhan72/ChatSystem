package com.example.chatsystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.chat.protocol.RegisterDeviceRequest
import com.example.chatsystem.presentation.navigation.AppRoot
import com.example.chatsystem.ui.theme.ChatSystemTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Nguoi dung da phan hoi quyen thong bao Notification
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ChatApplication).container
        val initialConversationId = intent.getLongExtra("conversationId", 0L).takeIf { it > 0 }

        // Xin quyen hien thi Notification tren Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Dang ky FCM token voi backend de san sang nhan Push Notification khi offline
        lifecycleScope.launch {
            container.sessionStore.session.collect { session ->
                if (session != null) {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            lifecycleScope.launch {
                                runCatching { container.api.registerDevice(RegisterDeviceRequest(token)) }
                            }
                        }
                    }
                }
            }
        }

        setContent {
            ChatSystemTheme {
                AppRoot(container, initialConversationId)
            }
        }
    }
}
