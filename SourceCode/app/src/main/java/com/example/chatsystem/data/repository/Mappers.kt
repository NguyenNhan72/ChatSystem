package com.example.chatsystem.data.repository

import com.example.chat.protocol.ConversationDto
import com.example.chat.protocol.MessageDto
import com.example.chatsystem.data.local.entity.ConversationEntity
import com.example.chatsystem.data.local.entity.MemberEntity
import com.example.chatsystem.data.local.entity.MessageEntity

fun ConversationDto.toEntity() = ConversationEntity(
    id = id,
    type = type,
    title = title,
    lastSeq = lastSeq,
    unreadCount = unreadCount,
    lastMessageText = lastMessage?.content,
    lastMessageSenderId = lastMessage?.senderId,
    lastActivityAt = lastMessage?.createdAt ?: createdAt,
)

fun ConversationDto.toMemberEntities() = members.map {
    MemberEntity(id, it.user.id, it.user.username, it.user.displayName, it.lastDeliveredSeq, it.lastReadSeq, it.online, it.lastActiveAt)
}

/** Mot tin nhan den tu server thi mac dinh da duoc luu tren server: trang thai SENT. */
fun MessageDto.toEntity() = MessageEntity(clientMsgId, conversationId, seq, senderId, content, createdAt, MessageEntity.SENT)
