package com.example.chatsystem

import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.data.local.entity.MessageEntity
import com.example.chatsystem.presentation.viewmodel.DeliveryStatus
import com.example.chatsystem.presentation.viewmodel.toMessageItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** UI chi duoc hien thi trang thai ma he thong thuc su xac minh duoc: ack => Sent, cursor nguoi nhan => Delivered / Read. */
class MessageStatusTest {
    private val me = 1L
    private val bob = 2L

    private fun msg(id: String, seq: Long?, sender: Long = me, status: String = MessageEntity.SENT) =
        MessageEntity(id, 7, seq, sender, "text $id", 1_000, status)

    private fun members(bobDelivered: Long, bobRead: Long) = listOf(
        MemberEntity(7, me, "alice", "Alice", 0, 0, true, null),
        MemberEntity(7, bob, "bob", "Bob", bobDelivered, bobRead, false, null),
    )

    private fun lastStatus(bobDelivered: Long, bobRead: Long) =
        toMessageItems(listOf(msg("a", 1), msg("b", 2)), members(bobDelivered, bobRead), myUserId = me, isGroup = false).map { it.status }

    @Test
    fun `direct chat status follows the recipient's cursors, shown only on my last message`() {
        assertEquals(listOf(null, DeliveryStatus.READ), lastStatus(bobDelivered = 2, bobRead = 2))
        assertEquals(listOf(null, DeliveryStatus.DELIVERED), lastStatus(bobDelivered = 2, bobRead = 1))
        assertEquals(listOf(null, DeliveryStatus.SENT), lastStatus(bobDelivered = 1, bobRead = 1))
    }

    @Test
    fun `unconfirmed messages always show Sending or Failed`() {
        val messages = listOf(
            msg("a", 1),
            msg("d", null, status = MessageEntity.SENDING),
            msg("e", null, status = MessageEntity.FAILED),
        )
        val items = toMessageItems(messages, members(bobDelivered = 1, bobRead = 1), myUserId = me, isGroup = false)
        assertEquals(listOf(null, DeliveryStatus.SENDING, DeliveryStatus.FAILED), items.map { it.status })
    }

    @Test
    fun `other people's messages carry no status`() {
        val item = toMessageItems(listOf(msg("x", 5, sender = bob)), members(5, 5), myUserId = me, isGroup = false).single()
        assertNull(item.status)
        assertEquals(false, item.mine)
        assertNull(item.senderName) // 1-1: khong hien thi ten tren bong bong
    }

    @Test
    fun `group messages stay at Sent and show the sender's name`() {
        val members = members(bobDelivered = 9, bobRead = 9)
        val items = toMessageItems(listOf(msg("a", 1), msg("b", 2, sender = bob)), members, myUserId = me, isGroup = true)
        assertEquals(DeliveryStatus.SENT, items[0].status) // ke ca khi cursor cua Bob da vuot qua
        assertEquals("Bob", items[1].senderName)
    }
}
