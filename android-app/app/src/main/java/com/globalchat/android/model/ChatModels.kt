package com.globalchat.android.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

@Serializable
data class ChatPayload(
    val id: String,
    val chatId: String,
    val senderId: String,
    val receiverIds: List<String>,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val type: String = "TEXT",
    val replyToId: String? = null,
    val replyToText: String? = null,
    val reaction: String? = null,
    val isGroup: Boolean = false,
    val groupName: String? = null
)

data class StatusUpdateItem(
    val userName: String,
    val timeLabel: String,
    val caption: String,
    val viewed: Boolean = false
)

data class CallLogItem(
    val name: String,
    val timeLabel: String,
    val isVideo: Boolean,
    val isMissed: Boolean,
    val isIncoming: Boolean
)