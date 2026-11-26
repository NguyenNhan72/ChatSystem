package com.example.chatsystem.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mot tin nhan duoc cache cuc bo — dong thoi la hang doi Outbox: tin do toi viet duoc luu tai day truoc (SENDING)
 * va chi duoc cap seq khi server gui ack xac nhan.
 *
 * Khoa chinh = clientMsgId (UUID do thiet bi tu sinh). Cung mot tin co the den thiet bi qua 3 con duong:
 * ack xac nhan tin vua gui, push truc tiep qua websocket, hoac trang dong bo sau reconnect — moi duong deu
 * upsert vao cung mot dong nen khong bao gio bi hien thi trung lap.
 */
@Entity(tableName = "messages", indices = [Index(value = ["conversationId", "seq"])])
data class MessageEntity(
    @PrimaryKey val clientMsgId: String,
    val conversationId: Long,
    val seq: Long?,          // null cho den khi server da luu va cap seq
    val senderId: Long,
    val content: String,
    val createdAt: Long,     // thoi gian tren server khi da luu; thoi gian thiet bi khi dang cho gui
    val status: String,      // SENDING | SENT | FAILED — con Delivered/Read duoc suy ra tu cursor cua doi phuong
    val reaction: String? = null, // emotion tha vao tin nhan (vi du: ❤️, 👍, 😆...)
) {
    companion object {
        const val SENDING = "SENDING"
        const val SENT = "SENT"
        const val FAILED = "FAILED"
    }
}
