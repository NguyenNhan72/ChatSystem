package com.example.chatsystem.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cache cuc bo cua mot dong hoi thoai tren man hinh danh sach (sach phan wrap-up: cache phia client). */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val type: String,               // DIRECT (1-1) hoac GROUP (nhom)
    val title: String,
    val lastSeq: Long,              // seq moi nhat tren server; > MAX(seq) trong Room co nghia la dang bi thieu tin cuc bo
    val unreadCount: Int,
    val lastMessageText: String?,
    val lastMessageSenderId: Long?,
    val lastActivityAt: Long,       // thoi gian tin nhan cuoi hoac thoi gian tao; khoa sap xep danh sach
)

@Entity(
    tableName = "conversation_members",
    primaryKeys = ["conversationId", "userId"],
    indices = [Index("userId")],
)
data class MemberEntity(
    val conversationId: Long,
    val userId: Long,
    val username: String,
    val displayName: String,
    val lastDeliveredSeq: Long,     // con tro da nhan: tin nhan cua toi co seq <= gia tri nay thi coi la Da nhan (Delivered)
    val lastReadSeq: Long,
    val online: Boolean,            // presence: khoi tao tu REST va duoc cap nhat lien tuc qua event presence
    val lastActiveAt: Long?,        // last_active_at cua user (cap nhat khi online va khi offline)
)
