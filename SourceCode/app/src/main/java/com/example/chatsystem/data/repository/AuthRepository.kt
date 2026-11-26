package com.example.chatsystem.data.repository

import com.example.chat.protocol.AuthResponse
import com.example.chat.protocol.LoginRequest
import com.example.chat.protocol.RegisterRequest
import com.example.chatsystem.data.local.GuestIdentity
import com.example.chatsystem.data.local.Session
import com.example.chatsystem.data.local.SessionStore
import com.example.chatsystem.data.local.database.AppDatabase
import com.example.chatsystem.data.remote.api.ChatApi
import com.example.chatsystem.data.remote.resultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.security.SecureRandom
import java.util.UUID

/** Tao tai khoan khach moi cho thiet bi: username dang "guest_xxxxxxxx" (hop le voi quy tac a-z0-9_ cua server). */
fun newGuestIdentity(random: java.util.Random = SecureRandom()): GuestIdentity {
    val id = (1..8).map { "0123456789abcdef"[random.nextInt(16)] }.joinToString("")
    return GuestIdentity(username = "guest_$id", displayName = "Khách ${id.take(4).uppercase()}", password = UUID.randomUUID().toString())
}

/**
 * Khong can man hinh dang nhap: moi thiet bi tu dang nhap bang tai khoan khach rieng, tao o lan dau mo app.
 * Phia server van giu nguyen quy trinh (register / login / JWT); app chi tu dong thuc hien ma khong can nguoi dung nhap.
 */
class AuthRepository(
    private val api: ChatApi,
    private val sessionStore: SessionStore,
    private val db: AppDatabase,
) {
    val session: StateFlow<Session?> = sessionStore.session

    /** Duoc goi moi khi chua co session: lan dau mo app, hoac khi token bi tu choi (401). */
    suspend fun signIn(): Result<Unit> = resultOf {
        val saved = sessionStore.guest()
        if (saved != null) {
            try {
                // Cung tai khoan nhu truoc: giu nguyen cache Room va outbox.
                return@resultOf save(api.login(LoginRequest(saved.username, saved.password)))
            } catch (e: HttpException) {
                if (e.code() != 401) throw e // 401 = tai khoan cu da bi xoa (khi reset database)
            }
        }
        // Khong tao tai khoan guest: mac dinh su dung tai khoan demo Nhân Nguyễn
        switchAccount("nhan", "Nhân Nguyễn")
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { api.unregisterDevice() }
        sessionStore.clear()
        db.clearAllTables()
        sessionStore.saveCurMaxInboxId(0)
    }

    /** Chuyen doi nhanh giua cac tai khoan demo (Nhan Nguyen, P Tien, P Minh) de test truc quan tren emulator. */
    suspend fun switchAccount(username: String, displayName: String): Result<Unit> = resultOf {
        val password = "demopass1"
        // Go token FCM khoi tai khoan cu, neu khong push cua tai khoan cu van ve may nay
        if (sessionStore.session.value != null) runCatching { api.unregisterDevice() }
        val auth = try {
            api.login(LoginRequest(username, password))
        } catch (e: Exception) {
            api.register(RegisterRequest(username, displayName, password))
        }
        sessionStore.saveGuest(GuestIdentity(username, displayName, password))
        withContext(Dispatchers.IO) { db.clearAllTables() }
        sessionStore.saveCurMaxInboxId(0) // cache Room trong => thiet bi dong bo lai tu dau inbox
        save(auth)
    }

    private fun save(auth: AuthResponse) =
        sessionStore.save(Session(auth.token, auth.user.id, auth.user.username, auth.user.displayName, auth.chatServerUrl))
}
