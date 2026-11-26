package com.example.chatsystem.data.remote.api

import com.example.chat.protocol.AddMembersRequest
import com.example.chat.protocol.AuthResponse
import com.example.chat.protocol.ChatServerDto
import com.example.chat.protocol.ConversationDto
import com.example.chat.protocol.CreateDirectRequest
import com.example.chat.protocol.CreateGroupRequest
import com.example.chat.protocol.InboxItemDto
import com.example.chat.protocol.LoginRequest
import com.example.chat.protocol.MessageDto
import com.example.chat.protocol.RegisterDeviceRequest
import com.example.chat.protocol.RegisterRequest
import com.example.chat.protocol.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Goi REST toi API server (sach: stateless services). Luong tin nhan thoi gian thuc chay qua WebSocket. */
interface ChatApi {
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/users") suspend fun searchUsers(@Query("q") query: String): List<UserDto>
    @POST("api/users/me/device") suspend fun registerDevice(@Body body: RegisterDeviceRequest)
    @DELETE("api/users/me/device") suspend fun unregisterDevice()

    @GET("api/conversations") suspend fun conversations(): List<ConversationDto>
    @GET("api/conversations/{id}") suspend fun conversation(@Path("id") id: Long): ConversationDto
    @POST("api/conversations/direct") suspend fun openDirect(@Body body: CreateDirectRequest): ConversationDto
    @POST("api/conversations/group") suspend fun createGroup(@Body body: CreateGroupRequest): ConversationDto
    @POST("api/conversations/{id}/members") suspend fun addMembers(@Path("id") id: Long, @Body body: AddMembersRequest): ConversationDto

    /** Hinh 12-13: tin trong inbox (message sync queue) cua toi co inboxId > cur_max_message_id cua thiet bi. */
    @GET("api/sync") suspend fun sync(@Query("afterInboxId") afterInboxId: Long, @Query("limit") limit: Int): List<InboxItemDto>

    /** Service discovery (Hinh 12-11, Wrap up): xin chat server khac khi server [exclude] khong ket noi duoc. */
    @GET("api/discovery/chat-server") suspend fun chatServer(@Query("exclude") exclude: String?): ChatServerDto

    /** Trang lich su tin nhan tang dan theo seq. afterSeq = bu tin bi thieu; beforeSeq = cuon len; khong truyen gi = trang moi nhat. */
    @GET("api/conversations/{id}/messages")
    suspend fun messages(
        @Path("id") conversationId: Long,
        @Query("afterSeq") afterSeq: Long? = null,
        @Query("beforeSeq") beforeSeq: Long? = null,
        @Query("limit") limit: Int,
    ): List<MessageDto>
}
