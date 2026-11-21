package com.globalchat.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.globalchat.android.data.local.AppDatabase
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.data.local.MessageEntity
import com.globalchat.android.data.remote.ChatSocketClient
import com.globalchat.android.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).chatDao()
    private val socket = ChatSocketClient()

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet())
    val typingUsers: StateFlow<Set<String>> = _typingUsers.asStateFlow()

    val isConnected: StateFlow<Boolean> = socket.isConnected

    val chats: StateFlow<List<ChatEntity>> = dao.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<MessageEntity>> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId == null) flowOf(emptyList()) else dao.observeMessages(chatId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusUpdates = listOf(
        StatusUpdateItem("Amina Bello", "Today, 08:42", "Morning coffee in Lagos"),
        StatusUpdateItem("Dev Group", "Yesterday, 22:15", "New Kotlin build is live!", viewed = true)
    )
    val callLogs = listOf(
        CallLogItem("Amina Bello", "Today, 08:10", isVideo = true, isMissed = false, isIncoming = true),
        CallLogItem("+2348022222222", "Yesterday, 19:30", isVideo = false, isMissed = true, isIncoming = true)
    )

    init {
        viewModelScope.launch {
            socket.incoming.collect { handleIncoming(it) }
        }
    }

    fun signIn(phoneNumber: String, serverHost: String) {
        val clean = phoneNumber.trim()
        if (clean.isBlank()) return
        _myUserId.value = clean
        socket.start(viewModelScope, clean, serverHost)
    }

    fun signOut() {
        socket.stop()
        _myUserId.value = null
        _activeChatId.value = null
        viewModelScope.launch {
            dao.clearAllMessages()
            dao.clearAllChats()
        }
    }

    fun startChat(partnerPhone: String) {
        val clean = partnerPhone.trim()
        if (clean.isBlank() || clean == _myUserId.value) return
        viewModelScope.launch {
            if (dao.getChat(clean) == null) {
                dao.upsertChat(
                    ChatEntity(
                        chatId = clean,
                        title = clean,
                        isGroup = false,
                        memberIdsCsv = clean,
                        lastMessage = "Tap to start chatting",
                        lastTimestamp = System.currentTimeMillis()
                    )
                )
            }
            openChat(clean)
        }
    }

    fun createGroup(groupName: String, memberPhonesCsv: String) {
        val me = _myUserId.value ?: return
        val members = memberPhonesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .plus(me)
            .distinct()
        if (members.size < 2 || groupName.isBlank()) return
        val groupId = "grp_${UUID.randomUUID().toString().take(8)}"

        viewModelScope.launch {
            dao.upsertChat(
                ChatEntity(
                    chatId = groupId,
                    title = groupName.trim(),
                    isGroup = true,
                    memberIdsCsv = members.joinToString(","),
                    lastMessage = "Group created",
                    lastTimestamp = System.currentTimeMillis()
                )
            )
            openChat(groupId)
        }
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        viewModelScope.launch {
            dao.clearUnread(chatId)
            val chat = dao.getChat(chatId) ?: return@launch
            val me = _myUserId.value ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(
                ChatPayload(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    type = "ACK_READ",
                    isGroup = chat.isGroup
                )
            )
        }
    }

    fun closeChat() {
        _activeChatId.value = null
    }

    fun sendMessage(text: String, replyTo: MessageEntity? = null) {
        val me = _myUserId.value ?: return
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            val msgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            dao.upsertMessage(
                MessageEntity(
                    id = msgId,
                    chatId = chatId,
                    senderId = me,
                    content = text,
                    timestamp = now,
                    status = MessageStatus.SENDING,
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.content
                )
            )
            dao.upsertChat(chat.copy(lastMessage = text, lastTimestamp = now))

            socket.send(
                ChatPayload(
                    id = msgId,
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = text,
                    timestamp = now,
                    status = MessageStatus.SENDING,
                    type = "TEXT",
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.content,
                    isGroup = chat.isGroup,
                    groupName = if (chat.isGroup) chat.title else null
                )
            )
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val me = _myUserId.value ?: return
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(
                ChatPayload(
                    id = "typing-$me",
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = if (isTyping) "START" else "STOP",
                    timestamp = System.currentTimeMillis(),
                    type = "TYPING"
                )
            )
        }
    }

    fun sendReaction(messageId: String, emoji: String) {
        val me = _myUserId.value ?: return
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            dao.updateReaction(messageId, emoji)
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(
                ChatPayload(
                    id = messageId,
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    type = "REACTION",
                    reaction = emoji
                )
            )
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        val me = _myUserId.value ?: return
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            dao.markDeleted(messageId)
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(
                ChatPayload(
                    id = messageId,
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    type = "DELETE"
                )
            )
        }
    }

    private suspend fun handleIncoming(payload: ChatPayload) {
        val me = _myUserId.value ?: return
        val resolvedChatId = if (payload.isGroup) payload.chatId else payload.senderId

        when (payload.type) {
            "PRESENCE" -> {
                _onlineUsers.update { set ->
                    if (payload.content == "ONLINE") set + payload.senderId else set - payload.senderId
                }
            }
            "TYPING" -> {
                _typingUsers.update { set ->
                    if (payload.content == "START") set + payload.senderId else set - payload.senderId
                }
            }
            "ACK_SENT" -> dao.updateMessageStatus(payload.id, MessageStatus.SENT)
            "ACK_DELIVERED" -> dao.updateMessageStatus(payload.id, MessageStatus.DELIVERED)
            "ACK_READ" -> {
                dao.observeMessages(resolvedChatId).firstOrNull()?.forEach { m ->
                    if (m.senderId == me && m.status != MessageStatus.READ) {
                        dao.updateMessageStatus(m.id, MessageStatus.READ)
                    }
                }
            }
            "REACTION" -> dao.updateReaction(payload.id, payload.reaction)
            "DELETE" -> dao.markDeleted(payload.id)
            "TEXT" -> {
                val existingChat = dao.getChat(resolvedChatId)
                val isCurrentlyOpen = _activeChatId.value == resolvedChatId
                val unread = if (isCurrentlyOpen) 0 else (existingChat?.unreadCount ?: 0) + 1
                val membersCsv = if (payload.isGroup) {
                    (payload.receiverIds + payload.senderId).distinct().joinToString(",")
                } else payload.senderId

                dao.upsertChat(
                    ChatEntity(
                        chatId = resolvedChatId,
                        title = payload.groupName ?: existingChat?.title ?: payload.senderId,
                        isGroup = payload.isGroup,
                        memberIdsCsv = membersCsv,
                        lastMessage = payload.content,
                        lastTimestamp = payload.timestamp,
                        unreadCount = unread
                    )
                )
                dao.upsertMessage(
                    MessageEntity(
                        id = payload.id,
                        chatId = resolvedChatId,
                        senderId = payload.senderId,
                        content = payload.content,
                        timestamp = payload.timestamp,
                        status = MessageStatus.DELIVERED,
                        replyToId = payload.replyToId,
                        replyToText = payload.replyToText
                    )
                )
                if (isCurrentlyOpen) {
                    socket.send(
                        payload.copy(
                            id = UUID.randomUUID().toString(),
                            senderId = me,
                            receiverIds = listOf(payload.senderId),
                            type = "ACK_READ"
                        )
                    )
                }
            }
        }
    }
}