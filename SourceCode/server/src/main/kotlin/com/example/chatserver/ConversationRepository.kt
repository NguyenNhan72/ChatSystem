package com.example.chatserver

import com.example.chat.protocol.*
import java.sql.Connection
import java.sql.ResultSet

object ConversationRepository {

    fun isMember(c: Connection, conversationId: Long, userId: Long): Boolean =
        c.query("SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, userId) { true }
            .isNotEmpty()

    fun type(c: Connection, conversationId: Long): String? =
        c.query("SELECT type FROM conversations WHERE id = ?", conversationId) { it.getString(1) }.singleOrNull()

    fun memberIds(c: Connection, conversationId: Long): List<Long> =
        c.query("SELECT user_id FROM conversation_members WHERE conversation_id = ?", conversationId) { it.getLong(1) }

    fun getOrCreateDirect(c: Connection, userA: Long, userB: Long): Long {
        val key = "${minOf(userA, userB)}:${maxOf(userA, userB)}"
        val created = c.query(
            "INSERT INTO conversations (type, direct_key) VALUES ('DIRECT', ?) ON CONFLICT (direct_key) DO NOTHING RETURNING id", key,
        ) { it.getLong(1) }.singleOrNull()
        if (created != null) {
            addMembers(c, created, listOf(userA, userB))
            return created
        }
        return c.query("SELECT id FROM conversations WHERE direct_key = ?", key) { it.getLong(1) }.single()
    }

    fun createGroup(c: Connection, name: String, memberIds: List<Long>): Long {
        val id = c.query("INSERT INTO conversations (type, name) VALUES ('GROUP', ?) RETURNING id", name) { it.getLong(1) }.single()
        addMembers(c, id, memberIds)
        return id
    }

    /** Cac thanh vien da co san trong nhom se duoc bo qua. */
    fun addMembers(c: Connection, conversationId: Long, userIds: List<Long>) {
        c.update(
            "INSERT INTO conversation_members (conversation_id, user_id) SELECT ?, unnest(?::bigint[]) ON CONFLICT DO NOTHING",
            conversationId, userIds,
        )
    }

    /** Danh sach nguoi dung co chung it nhat mot cuoc tro chuyen voi [userId]: doi tuong nhan thong bao khi trang thai presence thay doi. */
    fun contactsOf(c: Connection, userId: Long): List<Long> =
        c.query(
            """SELECT DISTINCT other.user_id FROM conversation_members mine
               JOIN conversation_members other ON other.conversation_id = mine.conversation_id
               WHERE mine.user_id = ? AND other.user_id <> ?""",
            userId, userId,
        ) { it.getLong(1) }

    fun listForUser(c: Connection, userId: Long, isOnline: (Long) -> Boolean, onlyId: Long? = null): List<ConversationDto> {
        val rows = c.query(
            """SELECT c.id, c.type, c.name, c.last_seq, c.created_at AS conv_created_at,
                      m.seq, m.client_msg_id, m.sender_id, m.content, m.created_at,
                      (SELECT count(*) FROM messages x
                        WHERE x.conversation_id = c.id AND x.seq > me.last_read_seq AND x.sender_id <> me.user_id) AS unread
               FROM conversation_members me
               JOIN conversations c ON c.id = me.conversation_id
               LEFT JOIN messages m ON m.conversation_id = c.id AND m.seq = c.last_seq
               WHERE me.user_id = ? AND (?::bigint IS NULL OR c.id = ?)
               ORDER BY COALESCE(m.created_at, c.created_at) DESC""",
            userId, onlyId, onlyId,
        ) { rs -> Row(rs.getLong("id"), rs.getString("type"), rs.getString("name"), rs.getLong("last_seq"), rs.getInt("unread"), rs.lastMessage(), rs.getTimestamp("conv_created_at").time) }
        if (rows.isEmpty()) return emptyList()

        val members = c.query(
            """SELECT cm.conversation_id, u.id, u.username, u.display_name, cm.last_delivered_seq, cm.last_read_seq, u.last_active_at
               FROM conversation_members cm JOIN users u ON u.id = cm.user_id
               WHERE cm.conversation_id = ANY(?) ORDER BY u.id""",
            rows.map { it.id },
        ) { rs ->
            val user = UserDto(rs.getLong(2), rs.getString(3), rs.getString(4))
            rs.getLong(1) to MemberDto(user, rs.getLong(5), rs.getLong(6), isOnline(user.id), rs.getTimestamp(7)?.time)
        }.groupBy({ it.first }, { it.second })

        return rows.map { r ->
            val ms = members[r.id].orEmpty()
            val title = r.name ?: ms.firstOrNull { it.user.id != userId }?.user?.displayName ?: "?"
            ConversationDto(r.id, r.type, title, r.lastSeq, r.unread, r.lastMessage, r.createdAt, ms)
        }
    }

    private class Row(val id: Long, val type: String, val name: String?, val lastSeq: Long, val unread: Int, val lastMessage: MessageDto?, val createdAt: Long)

    private fun ResultSet.lastMessage(): MessageDto? {
        val seq = getLong("seq")
        if (wasNull()) return null
        return MessageDto(getLong("id"), seq, getString("client_msg_id"), getLong("sender_id"), getString("content"),
            getTimestamp("created_at").time)
    }
}
