package com.example.chatserver

import com.example.chat.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.delay
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Trang thai online presence, hinh 12-16 den 12-19 trong sach. Timeout x = 30 giay duoc rut ngan con 1 giay o day
 * (quet moi 200 ms) de kiem tra cac quy tac thoi gian ma khong can cho nua phut.
 */
class PresenceTest {

    @BeforeTest
    fun clean() = TestDb.clean()

    private val fast = 1.seconds
    private val sweep = 200.milliseconds

    private suspend fun HttpClient.direct(me: AuthResponse, other: AuthResponse): ConversationDto =
        postJson("/api/conversations/direct", CreateDirectRequest(other.user.id), me.token).body()

    private suspend fun HttpClient.memberAsSeenBy(viewer: AuthResponse, conversationId: Long, userId: Long): MemberDto =
        getAuth("/api/conversations/$conversationId", viewer.token).body<ConversationDto>().members.single { it.user.id == userId }

    @Test
    fun `connecting makes a user online for their contacts (Figure 12-16, 12-19)`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val b = client.connect(bob.token)

        client.connect(alice.token)
        val online = b.expect<PresenceEvent>()
        assertEquals(alice.user.id, online.userId)
        assertTrue(online.online)
        assertNotNull(online.lastActiveAt) // Hinh 12-16: login luu last_active_at
        val seen = client.memberAsSeenBy(bob, conv.id, alice.user.id)
        assertTrue(seen.online) // phia pull: REST API cung tra ve online
        assertEquals(online.lastActiveAt, seen.lastActiveAt)
    }

    @Test
    fun `a dropped connection goes offline only after the heartbeat timeout (Figure 12-18)`() = chatTest(fast, sweep) { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val conv = client.direct(alice, bob)
        val b = client.connect(bob.token)
        val a = client.connect(alice.token)
        b.expect<PresenceEvent>() // alice online

        a.close(CloseReason(CloseReason.Codes.GOING_AWAY, "network lost")) // khong phai dang xuat chu dong
        b.expectSilence(ms = 400, presence = true) // van "online" trong thoi gian cho gia han

        val offline = b.expect<PresenceEvent>(timeoutMs = 3_000)
        assertEquals(alice.user.id, offline.userId)
        assertFalse(offline.online)
        assertNotNull(offline.lastActiveAt)

        val seen = client.memberAsSeenBy(bob, conv.id, alice.user.id)
        assertFalse(seen.online)
        assertEquals(offline.lastActiveAt, seen.lastActiveAt) // duoc luu vao users.last_active_at
    }

    @Test
    fun `heartbeats keep a user online, and a quick reconnect does not flicker`() = chatTest(fast, sweep) { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        client.direct(alice, bob)
        val b = client.connect(bob.token)
        var a = client.connect(alice.token)
        b.expect<PresenceEvent>() // alice online

        repeat(6) { // 1.8 giay > timeout, gui heartbeat moi 300 ms
            a.emit(HeartbeatEvent)
            delay(300)
        }
        a.close(CloseReason(CloseReason.Codes.GOING_AWAY, "tunnel"))
        delay(300)
        a = client.connect(alice.token) // ket noi lai trong thoi gian timeout
        repeat(5) {
            a.emit(HeartbeatEvent)
            delay(300)
        }
        b.expectSilence(ms = 100, presence = true) // khong bi chop tat trang thai offline/online tai bat ky thoi diem nao
    }

    @Test
    fun `logout from the last device is immediate, from one of two devices is not (Figure 12-17)`() = chatTest { client ->
        // timeout mac dinh 30 giay: su kien offline trong vong 2 giay chi co the den tu luong dang xuat
        val alice = client.register("alice")
        val bob = client.register("bob")
        client.direct(alice, bob)
        val b = client.connect(bob.token)
        val phone = client.connect(alice.token)
        val laptop = client.connect(alice.token)
        b.expect<PresenceEvent>() // alice online (chi phat 1 lan, khong phat theo tung thiet bi)
        b.expectSilence(ms = 200, presence = true)

        phone.close(CloseReason(CloseReason.Codes.NORMAL, "logout"))
        b.expectSilence(ms = 500, presence = true) // laptop van dang ket noi

        laptop.close(CloseReason(CloseReason.Codes.NORMAL, "logout"))
        val offline = b.expect<PresenceEvent>(timeoutMs = 2_000)
        assertFalse(offline.online)
    }

    @Test
    fun `presence is only sent to users who share a conversation`() = chatTest { client ->
        val alice = client.register("alice")
        val bob = client.register("bob")
        val stranger = client.register("carol")
        client.direct(alice, bob)
        val s = client.connect(stranger.token)

        client.connect(alice.token)
        s.expectSilence(ms = 500, presence = true)
        assertNull(client.memberAsSeenBy(alice, client.direct(alice, bob).id, bob.user.id).lastActiveAt) // bob chua tung ket noi
    }
}
