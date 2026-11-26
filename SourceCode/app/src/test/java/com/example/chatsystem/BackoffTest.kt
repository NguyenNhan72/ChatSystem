package com.example.chatsystem

import com.example.chatsystem.data.remote.websocket.backoffDelayMs
import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    @Test
    fun `reconnect delay doubles from 1 s and is capped at 30 s`() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L, 30L), (0..7).map { backoffDelayMs(it) / 1000 })
        assertEquals(30_000L, backoffDelayMs(1_000)) // khong bi tran so sau nhieu lan that bai
    }
}
