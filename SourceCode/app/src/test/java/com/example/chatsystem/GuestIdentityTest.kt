package com.example.chatsystem

import com.example.chatsystem.data.repository.newGuestIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** App tu dang ky tai khoan khach ma khong hoi nguoi dung, nen cac gia tri sinh ra phai vuot qua kiem tra cua server. */
class GuestIdentityTest {
    private val serverUsernameRule = Regex("^[a-z0-9_]{3,32}$") // cung quy tac nhu trong server Routes.kt

    @Test
    fun `generated guest identity passes the server's validation`() {
        repeat(200) {
            val g = newGuestIdentity()
            assertTrue(g.username, serverUsernameRule.matches(g.username))
            assertTrue(g.password.length in 6..72)
            assertTrue(g.displayName.length in 1..64)
            assertEquals("Khách " + g.username.removePrefix("guest_").take(4).uppercase(), g.displayName)
        }
    }

    @Test
    fun `two devices get different accounts`() {
        assertEquals(1_000, List(1_000) { newGuestIdentity().username }.toSet().size)
    }
}
