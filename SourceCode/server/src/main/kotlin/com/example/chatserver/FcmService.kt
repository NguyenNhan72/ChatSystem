package com.example.chatserver

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Tich hop Firebase Cloud Messaging (FCM) tren Server theo Chapter 12 (Hinh 12-12 buoc 5b trong sach).
 * Chi gui push khi nguoi nhan khong co session WebSocket hoat dong.
 */
object FcmService {
    private val log = LoggerFactory.getLogger(FcmService::class.java)

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        val stream: InputStream? = this::class.java.classLoader.getResourceAsStream("firebase-service-account.json")
        if (stream == null) {
            log.warn("Khong tim thay file firebase-service-account.json trong resources, tinh nang Push Notification tam tat.")
            return
        }
        try {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
            }
            initialized = true
            log.info("Firebase Admin SDK da khoi tao thanh cong.")
        } catch (e: Exception) {
            log.error("Khoi tao Firebase Admin that bai: {}", e.message)
        }
    }

    fun sendPush(fcmToken: String, title: String, body: String, conversationId: Long, senderId: Long) {
        if (!initialized) return
        try {
            val message = Message.builder()
                .setToken(fcmToken)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build(),
                )
                .putData("conversationId", conversationId.toString())
                .putData("senderId", senderId.toString())
                .putData("title", title)
                .putData("body", body)
                .build()

            FirebaseMessaging.getInstance().sendAsync(message)
            log.info("Da phat Push Notification toi conversation {} qua FCM.", conversationId)
        } catch (e: Exception) {
            log.warn("Goi FCM that bai: {}", e.message)
        }
    }
}
