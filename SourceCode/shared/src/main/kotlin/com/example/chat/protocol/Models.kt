package com.example.chat.protocol

import kotlinx.serialization.Serializable

// ---- Cac model request/response REST. Thoi gian tinh bang epoch milliseconds (UTC). ----

@Serializable data class RegisterRequest(val username: String, val displayName: String, val password: String)
@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class UserDto(val id: Long, val username: String, val displayName: String, val online: Boolean = false)

/** chatServerUrl = cau tra loi "service discovery" theo Hinh 12-11 buoc 3: dia chi chat server de app mo WebSocket. */
@Serializable data class AuthResponse(val token: String, val user: UserDto, val chatServerUrl: String)

/** GET /api/discovery/chat-server: service discovery chon chat server (sach, Wrap up: server loi => cap server moi). */
@Serializable data class ChatServerDto(val chatServerUrl: String)

@Serializable data class CreateDirectRequest(val userId: Long)
@Serializable data class CreateGroupRequest(val name: String, val memberIds: List<Long>)
@Serializable data class AddMembersRequest(val userIds: List<Long>)

@Serializable data class MemberDto(
    val user: UserDto,
    val lastDeliveredSeq: Long,
    val lastReadSeq: Long,
    val online: Boolean,        // trang thai online tai thoi diem goi request (sach: lay trang thai khi mo chat)
    val lastActiveAt: Long?,    // last_active_at (Hinh 12-16): cap nhat khi online va khi offline; null = chua tung ket noi
)

@Serializable data class MessageDto(
    val conversationId: Long,
    val seq: Long,              // sequence cuc bo: duy nhat va tang dan trong tung hoi thoai
    val clientMsgId: String,    // khoa chong trung tin (idempotency key) do client tu sinh (UUID)
    val senderId: Long,
    val content: String,
    val createdAt: Long,
)

@Serializable data class ConversationDto(
    val id: Long,
    val type: String,            // DIRECT (1-1) hoac GROUP (nhom)
    val title: String,           // ten nhom, hoac ten hien thi cua doi phuong neu la 1-1
    val lastSeq: Long,           // seq cua tin cuoi cung trong hoi thoai
    val unreadCount: Int,
    val lastMessage: MessageDto?,
    val createdAt: Long,         // ket hop cung lastMessage.createdAt de sap xep danh sach hoi thoai
    val members: List<MemberDto>,
)

@Serializable data class ErrorResponse(val error: String, val message: String)

// Dang ky token thiet bi Firebase Cloud Messaging (FCM) phuc vu Push Notification khi offline
@Serializable data class RegisterDeviceRequest(val fcmToken: String)

/** Mot phan tu trong inbox (message sync queue) cua user, Hinh 12-14/12-15. inboxId tang dan trong inbox cua user do. */
@Serializable data class InboxItemDto(val inboxId: Long, val message: MessageDto)
