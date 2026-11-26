package com.example.chatserver

import java.sql.Connection

/** Quan ly token thiet bi FCM de gui Push Notification theo Chapter 12 (Hinh 12-12 buoc 5b). */
object DeviceTokenRepository {
    fun upsert(c: Connection, userId: Long, fcmToken: String) {
        c.update(
            """INSERT INTO device_tokens (user_id, fcm_token, updated_at)
               VALUES (?, ?, ?)
               ON CONFLICT (user_id) DO UPDATE SET fcm_token = EXCLUDED.fcm_token, updated_at = EXCLUDED.updated_at""",
            userId, fcmToken, System.currentTimeMillis(),
        )
    }

    fun get(c: Connection, userId: Long): String? =
        c.query("SELECT fcm_token FROM device_tokens WHERE user_id = ?", userId) { it.getString(1) }.singleOrNull()

    fun delete(c: Connection, userId: Long) {
        c.update("DELETE FROM device_tokens WHERE user_id = ?", userId)
    }
}
