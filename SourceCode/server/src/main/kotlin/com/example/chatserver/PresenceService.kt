package com.example.chatserver

import com.example.chat.protocol.*
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Trang thai hien dien truc tuyen (sach: presence servers, Hinh 12-16 den 12-19), duoc viet thanh mot service trong tien trinh.
 *
 * - Online  = co heartbeat (hoac ket noi moi) den trong khoang [timeout] (vi du trong sach: x = 30s, client gui heartbeat moi 5s).
 * - Mat ket noi dot ngot se KHONG danh dau offline ngay: dien thoai di qua ham song chap chon co the noi lai sau vai giay
 *   nen tranh lam cham xanh nhap nhay (Hinh 12-18). Chi co tac vu [sweep] danh dau offline sau [timeout].
 * - Dang xuat chu dong tu thiet bi cuoi cung se offline ngay lap tuc (Hinh 12-17).
 * - Moi thay doi deu duoc day toi cac user dang online co chung hoi thoai (tuong duong channel cap ban be o Hinh 12-19).
 *
 * Don gian hoa: trang thai luu trong RAM nen mang tinh cuc bo theo tien trinh; neu nhieu chat server se dung Redis va pub/sub.
 */
class PresenceService(
    private val db: Database,
    private val registry: ConnectionRegistry,
    private val timeout: Duration,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lastSeen = ConcurrentHashMap<Long, Long>() // userId -> thoi diem heartbeat cuoi (ms); khong co trong map = offline

    fun isOnline(userId: Long): Boolean = lastSeen.containsKey(userId)

    /** Heartbeat hoac ket noi moi. Chuyen tu offline -> online se duoc phat thong bao. */
    suspend fun heartbeat(userId: Long) {
        val at = now()
        val wasOnline = lastSeen.put(userId, at) != null
        if (!wasOnline) {
            // Hinh 12-16: login => luu last_active_at (trang thai online nam trong lastSeen)
            db.tx { it.update("UPDATE users SET last_active_at = ? WHERE id = ?", Timestamp(at), userId) }
            announce(userId, online = true, lastActiveAt = at)
        }
    }

    /** Dang xuat chu dong: offline ngay — tru khi user van con thiet bi khac dang ket noi. */
    suspend fun logout(userId: Long) {
        if (registry.isOnline(userId)) return
        if (lastSeen.remove(userId) != null) goOffline(userId)
    }

    /** Duoc goi dinh ky: nhung user im lang qua thoi gian [timeout] se bi coi la offline. */
    suspend fun sweep() {
        val cutoff = now() - timeout.inWholeMilliseconds
        for ((userId, seenAt) in lastSeen) {
            // remove(key, value): bo qua neu vua co heartbeat moi den
            if (seenAt < cutoff && lastSeen.remove(userId, seenAt)) goOffline(userId)
        }
    }

    private suspend fun goOffline(userId: Long) {
        val at = now()
        db.tx { it.update("UPDATE users SET last_active_at = ? WHERE id = ?", Timestamp(at), userId) }
        announce(userId, online = false, lastActiveAt = at)
    }

    private suspend fun announce(userId: Long, online: Boolean, lastActiveAt: Long?) {
        // Hinh 12-19: chi phat cho "ban be" (nguoi co chung hoi thoai), khong phat cho moi user dang online.
        val contacts = db.tx { ConversationRepository.contactsOf(it, userId) }
        registry.send(contacts, PresenceEvent(userId, online, lastActiveAt))
    }
}
