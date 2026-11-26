package com.example.chatsystem.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.chat.protocol.RegisterDeviceRequest
import com.example.chatsystem.ChatApplication
import com.example.chatsystem.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service nhan su kien tu Firebase Cloud Messaging (FCM).
 * Xu ly tao thong bao Notification khi nhan tin nhan luc app dang offline/dong ngam.
 */
class ChatFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext as? ChatApplication ?: return
        if (app.container.sessionStore.session.value != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    app.container.api.registerDevice(RegisterDeviceRequest(token))
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Tin nhắn mới"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val conversationId = remoteMessage.data["conversationId"]?.toLongOrNull() ?: 0L

        showNotification(title, body, conversationId)
    }

    private fun showNotification(title: String, body: String, conversationId: Long) {
        val channelId = "chat_messages_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tin nhắn trò chuyện",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Kênh nhận thông báo tin nhắn trò chuyện"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(conversationId.toInt(), notification)
    }
}
