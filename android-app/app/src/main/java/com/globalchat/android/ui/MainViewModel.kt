package com.globalchat.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.globalchat.android.data.local.*
import com.globalchat.android.data.remote.ChatSocketClient
import com.globalchat.android.model.*
import com.google.firebase.messaging.FirebaseMessaging
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

    private val _pendingEmail = MutableStateFlow("")
    val pendingEmail: StateFlow<String> = _pendingEmail.asStateFlow()

    private val _otpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = _otpError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

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
            syncFcmToken(saved.serverHost, saved.phoneNumber)
        }
    }

    private fun syncFcmToken(serverHost: String, phone: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        socket.registerFcmToken(serverHost, phone, token)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    fun acceptTerms() { _onboardingStep.value = OnboardingStep.PHONE }

    fun submitPhone(dialCode: String, localNumber: String, email: String) {
        val digits = localNumber.filter { it.isDigit() }.dropWhile { it == '0' }
        val cleanEmail = email.trim()
        if (digits.length < 6 || !cleanEmail.contains("@")) {
            _otpError.value = "Enter a valid phone number and email address."
            return
        }
        val fullPhone = "$dialCode$digits"
        _pendingPhone.value = fullPhone
        _pendingEmail.value = cleanEmail
        _otpError.value = null
        _isAuthLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val res = socket.requestOtp(_profile.value.serverHost, fullPhone, cleanEmail)
            _isAuthLoading.value = false
            if (res.ok) _onboardingStep.value = OnboardingStep.OTP
            else _otpError.value = res.message
        }
    }

    fun resendOtp() {
        val phone = _pendingPhone.value
        val email = _pendingEmail.value
        if (phone.isBlank() || email.isBlank()) return
        _otpError.value = null
        _isAuthLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = socket.requestOtp(_profile.value.serverHost, phone, email)
            _isAuthLoading.value = false
            if (!res.ok) _otpError.value = res.message
        }
    }

    fun backToPhone() {
        _otpError.value = null
        _onboardingStep.value = OnboardingStep.PHONE
    }

    fun verifyOtp(code: String) {
        val phone = _pendingPhone.value
        _otpError.value = null
        _isAuthLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = socket.verifyOtp(_profile.value.serverHost, phone, code.trim())
            _isAuthLoading.value = false
            if (res.ok) _onboardingStep.value = OnboardingStep.PROFILE
            else _otpError.value = res.message
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
            socket.syncProfile(updated.serverHost, updated.phoneNumber, updated.displayName, updated.about)
            syncFcmToken(updated.serverHost, updated.phoneNumber)
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
            socket.syncProfile(updated.serverHost, updated.phoneNumber, updated.displayName, updated.about)
            syncFcmToken(updated.serverHost, updated.phoneNumber)
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

    fun loadDeviceContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = MediaHelper.readPhoneContacts(appContext)
            val myPhone = _profile.value.phoneNumber
            val myPrefix = supportedCountries.firstOrNull { myPhone.startsWith(it.dialCode) }?.dialCode ?: "+234"
            val normalized = raw.map { c ->
                val num = if (c.phoneNumber.startsWith("0") && c.phoneNumber.length >= 10) {
                    myPrefix + c.phoneNumber.drop(1)
                } else c.phoneNumber
                c.copy(phoneNumber = num)
            }
            _phoneContacts.value = normalized
            val registered = socket.syncContacts(_profile.value.serverHost, normalized.map { it.phoneNumber })
                .associateBy { it.phone }
            _phoneContacts.value = normalized.map { c ->
                val reg = registered[c.phoneNumber]
                if (reg != null) c.copy(isOnGlobalChat = true, about = reg.about) else c
            }.sortedByDescending { it.isOnGlobalChat }
        }
    }

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
        val members = memberPhonesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.plus(me).distinct()
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
            socket.send(ChatPayload(UUID.randomUUID().toString(), chatId, me, receivers, "", System.currentTimeMillis(), type = "ACK_READ", isGroup = chat.isGroup))
        }
    }

    fun closeChat() {
        MediaHelper.stopPlayback()
        _activeChatId.value = null
    }

    fun sendTextMessage(text: String, replyTo: MessageEntity? = null) = dispatchMessage(text, "TEXT", null, 0, replyTo)

    fun sendImageMessage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val b64 = MediaHelper.uriToCompressedBase64(appContext, uri) ?: return@launch
            dispatchMessage("📷 Photo", "IMAGE", b64, 0, null)
        }
    }

    fun sendVoiceMessage(base64: String, durationSec: Int) = dispatchMessage("🎤 Voice message (${durationSec}s)", "VOICE", base64, durationSec, null)

    private fun dispatchMessage(content: String, mediaType: String, mediaBase64: String?, durationSec: Int, replyTo: MessageEntity?) {
        val me = _profile.value.phoneNumber
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            val msgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            dao.upsertMessage(
                MessageEntity(msgId, chatId, me, content, now, MessageStatus.SENDING, mediaType, mediaBase64, durationSec, replyTo?.id, replyTo?.content)
            )
            dao.upsertChat(chat.copy(lastMessage = content, lastTimestamp = now))
            socket.send(
                ChatPayload(msgId, chatId, me, receivers, content, now, MessageStatus.SENDING, "TEXT", mediaType, mediaBase64, durationSec, replyTo?.id, replyTo?.content, isGroup = chat.isGroup, groupName = if (chat.isGroup) chat.title else null)
            )
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val me = _profile.value.phoneNumber
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(ChatPayload("typing-$me", chatId, me, receivers, if (isTyping) "START" else "STOP", System.currentTimeMillis(), type = "TYPING"))
        }
    }

    fun sendReaction(messageId: String, emoji: String) {
        val me = _profile.value.phoneNumber
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            dao.updateReaction(messageId, emoji)
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(ChatPayload(messageId, chatId, me, receivers, "", System.currentTimeMillis(), type = "REACTION", reaction = emoji))
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        val me = _profile.value.phoneNumber
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            dao.markDeleted(messageId)
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            socket.send(ChatPayload(messageId, chatId, me, receivers, "", System.currentTimeMillis(), type = "DELETE"))
        }
    }

    fun postStatus(caption: String, bgColorHex: Long, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val img = imageUri?.let { MediaHelper.uriToCompressedBase64(appContext, it, 900) }
            if (caption.isBlank() && img == null) return@launch
            dao.insertStatus(StatusEntity(UUID.randomUUID().toString(), "My status", _profile.value.phoneNumber, caption.trim(), img, bgColorHex, System.currentTimeMillis()))
        }
    }

    fun markStatusViewed(id: String) { viewModelScope.launch { dao.markStatusViewed(id) } }

    fun startCall(partnerId: String, title: String, isVideo: Boolean) {
        _activeCall.value = ActiveCallUiState(partnerId, title, isVideo)
        viewModelScope.launch { dao.insertCall(CallLogEntity(UUID.randomUUID().toString(), partnerId, title, System.currentTimeMillis(), isVideo)) }
    }

    fun endCall() { _activeCall.value = null }

    private suspend fun handleIncoming(payload: ChatPayload) {
        val me = _profile.value.phoneNumber
        val resolvedChatId = if (payload.isGroup) payload.chatId else payload.senderId
        when (payload.type) {
            "PRESENCE" -> _onlineUsers.update { if (payload.content == "ONLINE") it + payload.senderId else it - payload.senderId }
            "TYPING" -> _typingUsers.update { if (payload.content == "START") it + payload.senderId else it - payload.senderId }
            "ACK_SENT" -> dao.updateMessageStatus(payload.id, MessageStatus.SENT)
            "ACK_DELIVERED" -> dao.updateMessageStatus(payload.id, MessageStatus.DELIVERED)
            "ACK_READ" -> dao.observeMessages(resolvedChatId).firstOrNull()?.forEach { m ->
                if (m.senderId == me && m.status != MessageStatus.READ) dao.updateMessageStatus(m.id, MessageStatus.READ)
            }
            "REACTION" -> dao.updateReaction(payload.id, payload.reaction)
            "DELETE" -> dao.markDeleted(payload.id)
            "TEXT" -> {
                val existingChat = dao.getChat(resolvedChatId)
                val isOpen = _activeChatId.value == resolvedChatId
                val unread = if (isOpen) 0 else (existingChat?.unreadCount ?: 0) + 1
                val membersCsv = if (payload.isGroup) (payload.receiverIds + payload.senderId).distinct().joinToString(",") else payload.senderId
                dao.upsertChat(ChatEntity(resolvedChatId, payload.groupName ?: existingChat?.title ?: payload.senderId, payload.isGroup, membersCsv, payload.content, payload.timestamp, unread))
                dao.upsertMessage(MessageEntity(payload.id, resolvedChatId, payload.senderId, payload.content, payload.timestamp, MessageStatus.DELIVERED, payload.mediaType, payload.mediaBase64, payload.durationSec, payload.replyToId, payload.replyToText))
                if (isOpen && _profile.value.readReceipts) {
                    socket.send(payload.copy(id = UUID.randomUUID().toString(), senderId = me, receiverIds = listOf(payload.senderId), type = "ACK_READ"))
                }
            }
        }
    }
}