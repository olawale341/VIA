package com.globalchat.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.globalchat.android.data.local.*
import com.globalchat.android.data.remote.ChatSocketClient
import com.globalchat.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val dao = AppDatabase.getInstance(appContext).chatDao()
    private val prefs = ProfilePrefs(appContext)
    private val socket = ChatSocketClient()

    private val _profile = MutableStateFlow(prefs.load())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _onboardingStep = MutableStateFlow(
        if (prefs.load().isRegistered) OnboardingStep.DONE else OnboardingStep.WELCOME
    )
    val onboardingStep: StateFlow<OnboardingStep> = _onboardingStep.asStateFlow()

    private val _pendingPhone = MutableStateFlow("")
    val pendingPhone: StateFlow<String> = _pendingPhone.asStateFlow()

    private val _otpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = _otpError.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

    private val _phoneContacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val phoneContacts: StateFlow<List<PhoneContact>> = _phoneContacts.asStateFlow()

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

    val statuses: StateFlow<List<StatusEntity>> = dao.observeStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLogEntity>> = dao.observeCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            socket.incoming.collect { handleIncoming(it) }
        }
        val saved = _profile.value
        if (saved.isRegistered && saved.phoneNumber.isNotBlank()) {
            socket.start(viewModelScope, saved.phoneNumber, saved.serverHost)
        }
    }

    // --- Onboarding ---
    fun acceptTerms() {
        _onboardingStep.value = OnboardingStep.PHONE
    }

    fun submitPhone(dialCode: String, localNumber: String) {
        val digits = localNumber.filter { it.isDigit() }.dropWhile { it == '0' }
        if (digits.length < 6) return
        _pendingPhone.value = "$dialCode$digits"
        _otpError.value = null
        _onboardingStep.value = OnboardingStep.OTP
    }

    fun backToPhone() {
        _onboardingStep.value = OnboardingStep.PHONE
    }

    fun verifyOtp(code: String) {
        if (code.trim() == "123456") {
            _otpError.value = null
            _onboardingStep.value = OnboardingStep.PROFILE
        } else {
            _otpError.value = "Invalid code. Enter 123456 (Pre-Firebase test mode)"
        }
    }

    fun completeProfile(name: String, about: String, avatarUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val avatarB64 = avatarUri?.let { MediaHelper.uriToCompressedBase64(appContext, it, 360) }
            val updated = _profile.value.copy(
                phoneNumber = _pendingPhone.value,
                displayName = name.trim().ifBlank { _pendingPhone.value },
                about = about.trim().ifBlank { "Hey there! I am using GlobalChat." },
                avatarBase64 = avatarB64,
                isRegistered = true
            )
            prefs.save(updated)
            _profile.value = updated
            _onboardingStep.value = OnboardingStep.DONE
            socket.start(viewModelScope, updated.phoneNumber, updated.serverHost)
        }
    }

    fun updateProfileAndServer(name: String, about: String, serverHost: String, readReceipts: Boolean, avatarUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _profile.value
            val avatarB64 = if (avatarUri != null) MediaHelper.uriToCompressedBase64(appContext, avatarUri, 360) else current.avatarBase64
            val updated = current.copy(
                displayName = name.trim().ifBlank { current.displayName },
                about = about.trim().ifBlank { current.about },
                serverHost = serverHost.trim().ifBlank { current.serverHost },
                readReceipts = readReceipts,
                avatarBase64 = avatarB64
            )
            prefs.save(updated)
            _profile.value = updated
            socket.start(viewModelScope, updated.phoneNumber, updated.serverHost)
        }
    }

    fun logOut() {
        socket.stop()
        prefs.clear()
        _profile.value = UserProfile()
        _activeChatId.value = null
        _onboardingStep.value = OnboardingStep.WELCOME
        viewModelScope.launch {
            dao.clearAllMessages()
            dao.clearAllChats()
        }
    }

    // --- Contacts ---
    fun loadDeviceContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _phoneContacts.value = MediaHelper.readPhoneContacts(appContext)
        }
    }

    // --- Chats & Messaging ---
    fun startChat(partnerPhone: String, partnerName: String? = null) {
        val clean = partnerPhone.replace(Regex("[\\s()-]"), "")
        val me = _profile.value.phoneNumber
        if (clean.isBlank() || clean == me) return
        viewModelScope.launch {
            if (dao.getChat(clean) == null) {
                dao.upsertChat(
                    ChatEntity(
                        chatId = clean,
                        title = partnerName?.takeIf { it.isNotBlank() } ?: clean,
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
        val me = _profile.value.phoneNumber
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
                    lastMessage = "You created group \"${groupName.trim()}\"",
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
            if (!_profile.value.readReceipts) return@launch
            val chat = dao.getChat(chatId) ?: return@launch
            val me = _profile.value.phoneNumber
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
        MediaHelper.stopPlayback()
        _activeChatId.value = null
    }

    fun sendTextMessage(text: String, replyTo: MessageEntity? = null) {
        dispatchMessage(
            content = text,
            mediaType = "TEXT",
            mediaBase64 = null,
            durationSec = 0,
            replyTo = replyTo
        )
    }

    fun sendImageMessage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val b64 = MediaHelper.uriToCompressedBase64(appContext, uri) ?: return@launch
            dispatchMessage(
                content = "📷 Photo",
                mediaType = "IMAGE",
                mediaBase64 = b64,
                durationSec = 0,
                replyTo = null
            )
        }
    }

    fun sendVoiceMessage(base64: String, durationSec: Int) {
        dispatchMessage(
            content = "🎤 Voice message (${durationSec}s)",
            mediaType = "VOICE",
            mediaBase64 = base64,
            durationSec = durationSec,
            replyTo = null
        )
    }

    private fun dispatchMessage(
        content: String,
        mediaType: String,
        mediaBase64: String?,
        durationSec: Int,
        replyTo: MessageEntity?
    ) {
        val me = _profile.value.phoneNumber
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
                    content = content,
                    timestamp = now,
                    status = MessageStatus.SENDING,
                    mediaType = mediaType,
                    mediaBase64 = mediaBase64,
                    durationSec = durationSec,
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.content
                )
            )
            dao.upsertChat(chat.copy(lastMessage = content, lastTimestamp = now))

            socket.send(
                ChatPayload(
                    id = msgId,
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = content,
                    timestamp = now,
                    status = MessageStatus.SENDING,
                    type = "TEXT",
                    mediaType = mediaType,
                    mediaBase64 = mediaBase64,
                    durationSec = durationSec,
                    replyToId = replyTo?.id,
                    replyToText = replyTo?.content,
                    isGroup = chat.isGroup,
                    groupName = if (chat.isGroup) chat.title else null
                )
            )
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val me = _profile.value.phoneNumber
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
        val me = _profile.value.phoneNumber
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
        val me = _profile.value.phoneNumber
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

    // --- Status & Calls ---
    fun postStatus(caption: String, bgColorHex: Long, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val img = imageUri?.let { MediaHelper.uriToCompressedBase64(appContext, it, 900) }
            if (caption.isBlank() && img == null) return@launch
            dao.insertStatus(
                StatusEntity(
                    id = UUID.randomUUID().toString(),
                    ownerName = "My status",
                    ownerId = _profile.value.phoneNumber,
                    caption = caption.trim(),
                    imageBase64 = img,
                    bgColorHex = bgColorHex,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun markStatusViewed(id: String) {
        viewModelScope.launch { dao.markStatusViewed(id) }
    }

    fun startCall(partnerId: String, title: String, isVideo: Boolean) {
        _activeCall.value = ActiveCallUiState(partnerId, title, isVideo)
        viewModelScope.launch {
            dao.insertCall(
                CallLogEntity(
                    id = UUID.randomUUID().toString(),
                    partnerId = partnerId,
                    title = title,
                    timestamp = System.currentTimeMillis(),
                    isVideo = isVideo
                )
            )
        }
    }

    fun endCall() {
        _activeCall.value = null
    }

    private suspend fun handleIncoming(payload: ChatPayload) {
        val me = _profile.value.phoneNumber
        val resolvedChatId = if (payload.isGroup) payload.chatId else payload.senderId

        when (payload.type) {
            "PRESENCE" -> _onlineUsers.update { if (payload.content == "ONLINE") it + payload.senderId else it - payload.senderId }
            "TYPING" -> _typingUsers.update { if (payload.content == "START") it + payload.senderId else it - payload.senderId }
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
                val isOpen = _activeChatId.value == resolvedChatId
                val unread = if (isOpen) 0 else (existingChat?.unreadCount ?: 0) + 1
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
                        mediaType = payload.mediaType,
                        mediaBase64 = payload.mediaBase64,
                        durationSec = payload.durationSec,
                        replyToId = payload.replyToId,
                        replyToText = payload.replyToText
                    )
                )
                if (isOpen && _profile.value.readReceipts) {
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