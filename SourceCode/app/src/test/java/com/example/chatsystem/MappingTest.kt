package com.example.chatsystem

import com.example.chat.protocol.ConversationDto
import com.example.chat.protocol.MemberDto
import com.example.chat.protocol.MessageDto
import com.example.chat.protocol.UserDto
import com.example.chatsystem.data.remote.userMessageFor
import com.example.chatsystem.data.repository.toEntity
import com.example.chatsystem.data.repository.toMemberEntities
import com.example.chatsystem.presentation.components.formatListTime
import com.example.chatsystem.presentation.viewmodel.toItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Kiem tra logic chuyen doi giua network model, entity Room va du lieu hien thi len UI. */
class MappingTest {

    private val alice = UserDto(1, "alice", "Alice")
    private val bob = UserDto(2, "bob", "Bob")

    private fun conversation(lastMessage: MessageDto?) = ConversationDto(
        id = 7, type = "DIRECT", title = "Bob", lastSeq = lastMessage?.seq ?: 0, unreadCount = 3,
        lastMessage = lastMessage, createdAt = 1_000,
        members = listOf(MemberDto(alice, 0, 0, true, null), MemberDto(bob, 5, 4, false, 7_000)),
    )

    @Test
    fun `entity sorts by last message time, or creation time when empty`() {
        val msg = MessageDto(7, 5, "id", bob.id, "hi", createdAt = 9_000)
        assertEquals(9_000, conversation(msg).toEntity().lastActivityAt)
        assertEquals(1_000, conversation(null).toEntity().lastActivityAt)
        assertEquals("hi", conversation(msg).toEntity().lastMessageText)
    }

    @Test
    fun `members keep their delivery and read cursors`() {
        val members = conversation(null).toMemberEntities()
        assertEquals(listOf(1L, 2L), members.map { it.userId })
        assertEquals(5, members[1].lastDeliveredSeq)
        assertEquals(4, members[1].lastReadSeq)
        assertEquals(7, members[0].conversationId)
        assertEquals(true, members[0].online)
        assertEquals(7_000L, members[1].lastActiveAt)
    }

    @Test
    fun `list preview marks my own last message`() {
        val mine = conversation(MessageDto(7, 1, "a", alice.id, "see you", 2_000)).toEntity()
        val theirs = conversation(MessageDto(7, 1, "b", bob.id, "ok", 2_000)).toEntity()
        assertEquals("Bạn: see you", mine.toItem(myUserId = alice.id).preview)
        assertEquals("ok", theirs.toItem(myUserId = alice.id).preview)
        assertEquals("Chưa có tin nhắn", conversation(null).toEntity().toItem(alice.id).preview)
    }

    @Test
    fun `group preview names the sender, direct chat does not`() {
        val group = conversation(MessageDto(7, 1, "c", bob.id, "👍", 2_000)).copy(type = "GROUP").toEntity()
        assertEquals("Bob: 👍", group.toItem(myUserId = alice.id, senderName = "Bob").preview)
        assertEquals("Bạn: 👍", group.copy(lastMessageSenderId = alice.id).toItem(alice.id, "Alice").preview)
        val direct = conversation(MessageDto(7, 1, "d", bob.id, "👍", 2_000)).toEntity()
        assertEquals("👍", direct.toItem(myUserId = alice.id, senderName = "Bob").preview)
    }

    @Test
    fun `server error codes become Vietnamese messages, unknown codes keep the server text`() {
        assertEquals("Sai tên đăng nhập hoặc mật khẩu", userMessageFor("INVALID_CREDENTIALS", "x"))
        assertEquals("Tên đăng nhập đã tồn tại", userMessageFor("USERNAME_TAKEN", "x"))
        assertEquals("server says", userMessageFor("SOMETHING_NEW", "server says"))
        assertEquals("fallback", userMessageFor(null, "fallback"))
    }

    @Test
    fun `list time shows clock today, date otherwise`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 18, 20, 0) }.timeInMillis
        val earlierToday = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 18, 9, 5) }.timeInMillis
        val lastWeek = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 11, 9, 5) }.timeInMillis
        val lastYear = Calendar.getInstance().apply { set(2025, Calendar.MARCH, 2, 9, 5) }.timeInMillis
        assertEquals("09:05", formatListTime(earlierToday, now))
        assertEquals("11/11", formatListTime(lastWeek, now))
        assertEquals("02/03/2025", formatListTime(lastYear, now))
    }
}
