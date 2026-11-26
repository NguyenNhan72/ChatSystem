package com.example.chatserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ServiceDiscoveryTest {
    private val sd = ServiceDiscovery(listOf("ws://s1/ws", "ws://s2/ws", "ws://s3/ws"))

    @Test
    fun `a user keeps the same chat server while it is alive`() {
        assertEquals(sd.pick(7), sd.pick(7))
    }

    @Test
    fun `a failed server is replaced by another one (Wrap up)`() {
        val first = sd.pick(7)
        assertNotEquals(first, sd.pick(7, exclude = first))
        assertEquals("ws://only/ws", ServiceDiscovery(listOf("ws://only/ws")).pick(7, exclude = "ws://only/ws")) // khong con server nao khac
    }
}
