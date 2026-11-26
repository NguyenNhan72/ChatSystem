package com.example.chatserver

import com.example.chat.protocol.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

object MessageRepository {

    private const val COLUMNS = "conversation_id, seq, client_msg_id, sender_id, content, created_at"

    private fun ResultSet.toMessage() = MessageDto(
        getLong("conversation_id"), getLong("seq"), getString("client_msg_id"),
        getLong("sender_id"), getString("content"), getTimestamp("created_at").time,
    )

    fun byClientMsgId(c: Connection, clientMsgId: UUID): MessageDto? =
        c.query("SELECT $COLUMNS FROM messages WHERE client_msg_id = ?", clientMsgId) { it.toMessage() }.singleOrNull()

    /**
     * Sinh Message ID = co che "local sequence number generator" theo sach (Chuong 12, Message ID, cach 3).
     * Cau lenh UPDATE khoa dong conversation cho den khi commit, nen trong mot hoi thoai cac gia tri seq
     * duoc commit theo dung thu tu tang dan: client dong bo "seq > max cua toi" se khong bao gio bi bo sot
     * tin nhan commit muon hon voi seq nho hon. Neu insert bi rollback thi seq cung rollback nen khong tao lo hong.
     */
    fun insert(c: Connection, conversationId: Long, senderId: Long, clientMsgId: UUID, content: String): MessageDto {
        val seq = c.query("UPDATE conversations SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq", conversationId) {
            it.getLong(1)
        }.single()
        val message = c.query(
            "INSERT INTO messages (conversation_id, seq, client_msg_id, sender_id, content) VALUES (?, ?, ?, ?, ?) RETURNING $COLUMNS",
            conversationId, seq, clientMsgId, senderId, content,
        ) { it.toMessage() }.single()
        copyToInboxes(c, conversationId, seq)
        return message
    }

    /**
     * Hinh 12-14 trong sach: chep tin vao message sync queue (inbox) cua tung thanh vien, cung transaction voi insert.
     * Khoa dong users theo thu tu id tang dan => hai tin gui dong thoi vao hai nhom chung thanh vien khong bi deadlock;
     * khoa giu toi luc commit nen trong moi inbox, inbox_id duoc commit theo dung thu tu (thiet bi sync "> cur_max" khong sot tin).
     * NO KEY UPDATE (khong phai FOR UPDATE): khoa ngoai sender_id cua tin vua insert giu KEY SHARE tren dong user,
     * FOR UPDATE se xung dot voi khoa do va gay deadlock giua hai nhom gui dong thoi.
     */
    private fun copyToInboxes(c: Connection, conversationId: Long, seq: Long) {
        c.query(
            """SELECT u.id FROM users u JOIN conversation_members cm ON cm.user_id = u.id
               WHERE cm.conversation_id = ? ORDER BY u.id FOR NO KEY UPDATE OF u""",
            conversationId,
        ) { }
        c.update(
            """WITH bumped AS (
                   UPDATE users SET last_inbox_id = last_inbox_id + 1
                   WHERE id IN (SELECT user_id FROM conversation_members WHERE conversation_id = ?)
                   RETURNING id, last_inbox_id)
               INSERT INTO inbox (user_id, inbox_id, conversation_id, seq, client_msg_id, sender_id, content, created_at)
               SELECT b.id, b.last_inbox_id, m.conversation_id, m.seq, m.client_msg_id, m.sender_id, m.content, m.created_at
               FROM bumped b, messages m WHERE m.conversation_id = ? AND m.seq = ?""",
            conversationId, conversationId, seq,
        )
    }

    /** Hinh 12-13: tin moi cua thiet bi = tin trong inbox cua user hien tai co inbox_id > cur_max_message_id cua thiet bi. */
    fun inboxAfter(c: Connection, userId: Long, afterInboxId: Long, limit: Int): List<InboxItemDto> =
        c.query(
            "SELECT inbox_id, $COLUMNS FROM inbox WHERE user_id = ? AND inbox_id > ? ORDER BY inbox_id LIMIT ?",
            userId, afterInboxId, limit,
        ) { InboxItemDto(it.getLong("inbox_id"), it.toMessage()) }

    /**
     * Mot trang lich su tin nhan theo thu tu seq tang dan.
     * - afterSeq: tin nhan moi hon max local cua client (dong bo sau khi reconnect, Hinh 12-13).
     * - beforeSeq: tin nhan cu hon khi nguoi dung cuon len tren (phan trang).
     * - khong truyen gi: lay trang tin nhan moi nhat.
     */
    fun page(c: Connection, conversationId: Long, afterSeq: Long?, beforeSeq: Long?, limit: Int): List<MessageDto> = when {
        afterSeq != null -> c.query(
            "SELECT $COLUMNS FROM messages WHERE conversation_id = ? AND seq > ? ORDER BY seq LIMIT ?",
            conversationId, afterSeq, limit,
        ) { it.toMessage() }
        else -> c.query(
            "SELECT $COLUMNS FROM messages WHERE conversation_id = ? AND seq < ? ORDER BY seq DESC LIMIT ?",
            conversationId, beforeSeq ?: Long.MAX_VALUE, limit,
        ) { it.toMessage() }.reversed()
    }

    class Cursor(val deliveredSeq: Long, val readSeq: Long, val conversationType: String)

    /**
     * Di chuyen con tro delivered/read cua thanh vien chi theo chieu tien (GREATEST), khong vuot qua tin that cuoi cung
     * (dung LEAST voi last_seq), va dam bao delivered >= read. Tra ve null neu user khong thuoc hoi thoai.
     */
    fun moveCursor(c: Connection, conversationId: Long, userId: Long, deliveredSeq: Long, readSeq: Long): Cursor? =
        c.query(
            """UPDATE conversation_members cm
               SET last_read_seq      = GREATEST(cm.last_read_seq, LEAST(?, c.last_seq)),
                   last_delivered_seq = GREATEST(cm.last_delivered_seq, LEAST(GREATEST(?, ?), c.last_seq))
               FROM conversations c
               WHERE c.id = cm.conversation_id AND cm.conversation_id = ? AND cm.user_id = ?
               RETURNING cm.last_delivered_seq, cm.last_read_seq, c.type""",
            readSeq, deliveredSeq, readSeq, conversationId, userId,
        ) { Cursor(it.getLong(1), it.getLong(2), it.getString(3)) }.singleOrNull()
}
