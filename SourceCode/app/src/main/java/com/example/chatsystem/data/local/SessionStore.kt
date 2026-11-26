package com.example.chatsystem.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val token: String,
    val userId: Long,
    val username: String,
    val displayName: String,
    val chatServerUrl: String, // nhan tu luc login: dia chi chat server de mo WebSocket (Hinh 12-11 buoc 3)
)

/** Tai khoan khach cua thiet bi nay, duoc tao mot lan va dung de tu dong dang nhap lai khi gap loi 401. */
data class GuestIdentity(val username: String, val displayName: String, val password: String)

/**
 * Phien dang nhap duoc luu giu qua cac lan khoi dong app; null = chua dang nhap (hoac dang khoi tao).
 * Don gian hoa: luu JWT trong SharedPreferences noi bo cua app. Mat khau khach cung duoc luu tuong tu.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    // File rieng: khi bi loi 401 thi xoa token cu nhung van giu lai tai khoan khach de tu dong dang nhap lai.
    private val guestPrefs = context.getSharedPreferences("guest", Context.MODE_PRIVATE)

    fun guest(): GuestIdentity? {
        val username = guestPrefs.getString("username", null) ?: return null
        return GuestIdentity(username, guestPrefs.getString("displayName", "").orEmpty(), guestPrefs.getString("password", "").orEmpty())
    }

    fun saveGuest(g: GuestIdentity) = guestPrefs.edit {
        putString("username", g.username)
        putString("displayName", g.displayName)
        putString("password", g.password)
    }
    // Hinh 12-13: cur_max_message_id cua thiet bi = inboxId lon nhat da dong bo. File rieng de loi 401 (xoa token)
    // khong lam mat con tro trong khi cache Room van con; con tro duoc dat lai 0 moi khi xoa cache Room.
    private val syncPrefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun curMaxInboxId(): Long = syncPrefs.getLong("curMaxInboxId", 0)
    fun saveCurMaxInboxId(value: Long) = syncPrefs.edit { putLong("curMaxInboxId", value) }

    private val _session = MutableStateFlow(read())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private fun read(): Session? {
        val token = prefs.getString("token", null) ?: return null
        return Session(
            token = token,
            userId = prefs.getLong("userId", 0),
            username = prefs.getString("username", "").orEmpty(),
            displayName = prefs.getString("displayName", "").orEmpty(),
            chatServerUrl = prefs.getString("chatServerUrl", "").orEmpty(),
        )
    }

    fun save(s: Session) {
        prefs.edit {
            putString("token", s.token)
            putLong("userId", s.userId)
            putString("username", s.username)
            putString("displayName", s.displayName)
            putString("chatServerUrl", s.chatServerUrl)
        }
        _session.value = s
    }

    fun clear() {
        prefs.edit { clear() }
        _session.value = null
    }
}
