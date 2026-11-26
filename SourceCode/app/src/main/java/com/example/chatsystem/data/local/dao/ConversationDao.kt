package com.example.chatsystem.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    /** Room tu dong phat lai Flow khi bang thay doi, giup UI tu cap nhat sau moi lan sync. */
    @Query("SELECT * FROM conversations ORDER BY lastActivityAt DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId ORDER BY displayName")
    fun observeMembers(conversationId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM conversation_members ORDER BY displayName")
    fun observeAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: Long)

    /** Su kien receipt tien con tro cua thanh vien: xac dinh trang thai Da nhan / Da xem cua tin nhan. Con tro chi co tien. */
    @Query("""UPDATE conversation_members
              SET lastDeliveredSeq = MAX(lastDeliveredSeq, :deliveredSeq), lastReadSeq = MAX(lastReadSeq, :readSeq)
              WHERE conversationId = :conversationId AND userId = :userId""")
    suspend fun moveMemberCursor(conversationId: Long, userId: Long, deliveredSeq: Long, readSeq: Long)

    /** Su kien presence: mot user co the la thanh vien cua nhieu hoi thoai khac nhau. */
    @Query("UPDATE conversation_members SET online = :online, lastActiveAt = COALESCE(:lastActiveAt, lastActiveAt) WHERE userId = :userId")
    suspend fun setPresence(userId: Long, online: Boolean, lastActiveAt: Long?)

    /** Cac hoi thoai co nguoi khac dang online: dung de hien thi cham xanh tren danh sach. */
    @Query("SELECT DISTINCT conversationId FROM conversation_members WHERE online = 1 AND userId != :myUserId")
    fun observeConversationsWithSomeoneOnline(myUserId: Long): Flow<List<Long>>

    /** Danh sach ID cua cac user dang online trong bang members. */
    @Query("SELECT DISTINCT userId FROM conversation_members WHERE online = 1")
    fun observeOnlineUserIds(): Flow<List<Long>>

    @Upsert suspend fun upsert(conversations: List<ConversationEntity>)
    @Upsert suspend fun upsertMembers(members: List<MemberEntity>)

    @Query("DELETE FROM conversations WHERE id NOT IN (:keepIds)")
    suspend fun deleteConversationsNotIn(keepIds: List<Long>)

    @Query("DELETE FROM conversation_members WHERE conversationId IN (:conversationIds)")
    suspend fun deleteMembersOf(conversationIds: List<Long>)

    @Query("DELETE FROM conversation_members WHERE conversationId NOT IN (SELECT id FROM conversations)")
    suspend fun deleteOrphanMembers()

    @Query("DELETE FROM messages WHERE conversationId NOT IN (SELECT id FROM conversations)")
    suspend fun deleteOrphanMessages()

    /** Danh sach tu server la chan ly: xoa nhung hoi thoai khong con thuoc ve minh va lam moi danh sach thanh vien. */
    @Transaction
    suspend fun replaceAll(conversations: List<ConversationEntity>, members: List<MemberEntity>) {
        deleteConversationsNotIn(conversations.map { it.id })
        deleteOrphanMembers()
        deleteOrphanMessages()
        upsertOne(conversations, members)
    }

    /** Cap nhat hoi thoai va thay the toan bo danh sach thanh vien cua chung. */
    @Transaction
    suspend fun upsertOne(conversations: List<ConversationEntity>, members: List<MemberEntity>) {
        deleteMembersOf(conversations.map { it.id })
        upsert(conversations)
        upsertMembers(members)
    }
}
