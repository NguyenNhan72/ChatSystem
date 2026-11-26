package com.example.chatsystem.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.chatsystem.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Sap xep theo seq (thu tu tren server); tin chua gui (seq = null) nam o cuoi theo thu tu luc tao. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY CASE WHEN seq IS NULL THEN 1 ELSE 0 END, seq, createdAt")
    fun observe(conversationId: Long): Flow<List<MessageEntity>>

    @Upsert suspend fun upsert(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE clientMsgId = :clientMsgId")
    suspend fun get(clientMsgId: String): MessageEntity?

    /** Gia tri cur_max_message_id cuc bo theo Hinh 12-13 — tinh theo tung hoi thoai vi seq tang theo hoi thoai. */
    @Query("SELECT MAX(seq) FROM messages WHERE conversationId = :conversationId")
    suspend fun maxSeq(conversationId: Long): Long?

    @Query("SELECT MIN(seq) FROM messages WHERE conversationId = :conversationId")
    suspend fun minSeq(conversationId: Long): Long?

    /** seq cao nhat luu tru phia duoi [seq]: giup phat hien co tin nhan bi thieu o giua hay khong de goi sync. */
    @Query("SELECT MAX(seq) FROM messages WHERE conversationId = :conversationId AND seq < :seq")
    suspend fun maxSeqBelow(conversationId: Long, seq: Long): Long?

    /** Hang doi Outbox: moi tin chua duoc server xac nhan (SENDING hoac FAILED), tin cu nhat len truoc. */
    @Query("SELECT * FROM messages WHERE status IN ('SENDING', 'FAILED') ORDER BY createdAt")
    suspend fun pending(): List<MessageEntity>

    @Query("UPDATE messages SET seq = :seq, createdAt = :createdAt, status = 'SENT' WHERE clientMsgId = :clientMsgId")
    suspend fun markSent(clientMsgId: String, seq: Long, createdAt: Long)

    @Query("UPDATE messages SET status = 'SENDING' WHERE clientMsgId = :clientMsgId AND status = 'FAILED'")
    suspend fun markRetrying(clientMsgId: String)

    /** Khong nhan duoc ack dung han (hoac server tu choi). Khong bao gio ha cap tin nhan da SENT sang FAILED. */
    @Query("UPDATE messages SET status = 'FAILED' WHERE clientMsgId = :clientMsgId AND status = 'SENDING'")
    suspend fun markFailed(clientMsgId: String)

    @Query("UPDATE messages SET reaction = :reaction WHERE clientMsgId = :clientMsgId")
    suspend fun updateReaction(clientMsgId: String, reaction: String?)
}
