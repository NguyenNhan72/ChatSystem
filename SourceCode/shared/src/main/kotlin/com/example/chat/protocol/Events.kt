package com.example.chat.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Giao thuc WebSocket. Moi frame la mot doi tuong JSON co truong "type" chi dinh loai event, vi du:
 * {"type":"send","clientMsgId":"…","conversationId":1,"content":"hi"}
 */
object Protocol {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true // bo qua cac truong chua biet giup he thong tuong thich xuoi/nguoc
    }

    fun encode(event: ServerEvent): String = json.encodeToString(ServerEvent.serializer(), event)
    fun encode(event: ClientEvent): String = json.encodeToString(ClientEvent.serializer(), event)
    fun decodeClient(text: String): ClientEvent = json.decodeFromString(ClientEvent.serializer(), text)
    fun decodeServer(text: String): ServerEvent = json.decodeFromString(ServerEvent.serializer(), text)
}

// ---- Client gui len Server (Client -> Server) ----

@Serializable sealed interface ClientEvent

/** Gui tin nhan moi. Gui lai cung clientMsgId la an toan (idempotent): server chi luu 1 lan va tra ve ack cu. */
@Serializable @SerialName("send")
data class SendEvent(val clientMsgId: String, val conversationId: Long, val content: String) : ClientEvent

/** Thiet bi nay da luu (Room) moi tin nhan cua hoi thoai den seq nay. */
@Serializable @SerialName("delivered")
data class DeliveredEvent(val conversationId: Long, val seq: Long) : ClientEvent

/** Nguoi dung da xem moi tin nhan cua hoi thoai den seq nay. */
@Serializable @SerialName("read")
data class ReadEvent(val conversationId: Long, val seq: Long) : ClientEvent

/** Tha emotion / cam xuc vao mot tin nhan (vi du: ❤️, 👍, 😆...) */
@Serializable @SerialName("react")
data class ReactEvent(val conversationId: Long, val messageId: String, val reaction: String) : ClientEvent

/** Hinh 12-18 trong sach: gui dinh ky moi 5 giay khi app dang ket noi. Qua 30 giay khong co heartbeat se coi la offline. */
@Serializable @SerialName("heartbeat")
data object HeartbeatEvent : ClientEvent

// ---- Server day ve Client (Server -> Client) ----

@Serializable sealed interface ServerEvent

/**
 * Gui mot lan duy nhat ngay sau khi server da dang ky socket nay.
 * Client chi bat dau dong bo sau event nay de khong bi sot tin giua luc sync va live push.
 */
@Serializable @SerialName("connected")
data class ConnectedEvent(val userId: Long) : ServerEvent

/** Tin nhan da duoc commit thanh cong vao database: chuyen trang thai tu Dang gui (Sending) sang Da gui (Sent). */
@Serializable @SerialName("ack")
data class AckEvent(val clientMsgId: String, val conversationId: Long, val seq: Long, val createdAt: Long) : ServerEvent

/** Phat emotion duoc tha vao tin nhan toi cac thanh vien trong hoi thoai. */
@Serializable @SerialName("reaction")
data class ReactionEvent(val conversationId: Long, val messageId: String, val userId: Long, val reaction: String) : ServerEvent

/** Tin nhan moi danh cho nguoi nhan, hoac dong bo toi cac thiet bi khac cua chinh nguoi gui. */
@Serializable @SerialName("message")
data class MessageEvent(val message: MessageDto) : ServerEvent

/** Con tro da nhan / da xem cua mot thanh vien tien len: cap nhat trang thai Da nhan (Delivered) hoac Da xem (Read). */
@Serializable @SerialName("receipt")
data class ReceiptEvent(val conversationId: Long, val userId: Long, val deliveredSeq: Long, val readSeq: Long) : ServerEvent

/** Hinh 12-19 trong sach: co nguoi dung online/offline. Phat toi nhung nguoi dang online co chung hoi thoai voi userId. */
@Serializable @SerialName("presence")
data class PresenceEvent(val userId: Long, val online: Boolean, val lastActiveAt: Long?) : ServerEvent

/** Event tu client bi tu choi. clientMsgId co gia tri neu event loi la send. */
@Serializable @SerialName("error")
data class ErrorEvent(val clientMsgId: String?, val code: String, val message: String) : ServerEvent
