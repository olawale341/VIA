package com.globalchat.android.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.globalchat.android.crypto.E2eeCrypto
import com.globalchat.android.data.local.*
import com.globalchat.android.data.remote.ChatSocketClient
import com.globalchat.android.model.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SurfaceViewRenderer
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

    private var pendingIncomingOfferSdp: String? = null

    val rtcManager: WebRtcManager by lazy {
        WebRtcManager(
            context = appContext,
            onLocalSdpReady = { isOffer, sdp ->
                val call = _activeCall.value ?: return@WebRtcManager
                val me = _profile.value.phoneNumber
                viewModelScope.launch {
                    socket.send(
                        ChatPayload(
                            id = UUID.randomUUID().toString(),
                            chatId = call.partnerId,
                            senderId = me,
                            receiverIds = listOf(call.partnerId),
                            content = sdp,
                            timestamp = System.currentTimeMillis(),
                            type = if (isOffer) "CALL_OFFER" else "CALL_ANSWER",
                            callVideo = call.isVideo
                        )
                    )
                }
            },
            onIceCandidateReady = { ice ->
                val call = _activeCall.value ?: return@WebRtcManager
                val me = _profile.value.phoneNumber
                viewModelScope.launch {
                    socket.send(
                        ChatPayload(
                            id = UUID.randomUUID().toString(),
                            chatId = call.partnerId,
                            senderId = me,
                            receiverIds = listOf(call.partnerId),
                            content = ice.sdp,
                            timestamp = System.currentTimeMillis(),
                            type = "CALL_ICE",
                            sdpMid = ice.sdpMid,
                            sdpMLineIndex = ice.sdpMLineIndex
                        )
                    )
                }
            },
            onConnected = { _activeCall.update { it?.copy(status = CallStatus.CONNECTED) } },
            onClosed = { if (_activeCall.value != null) endCall(sendSignal = false) }
        )
    }

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
            syncKeysAndPush(saved.serverHost, saved.phoneNumber)
            refreshStatuses()
        }
    }

    private fun syncKeysAndPush(serverHost: String, phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val myPub = E2eeCrypto.getMyPublicKeyBase64(appContext)
            socket.uploadPublicKey(serverHost, phone, myPub)
            val p = _profile.value
            socket.syncProfile(serverHost, phone, p.displayName, p.about, p.avatarBase64)
        }
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
            val server = _profile.value.serverHost
            if (prefs.isVerifiedOnThisDevice(fullPhone, cleanEmail)) {
                val remote = socket.fetchProfile(server, fullPhone)
                val cached = prefs.load()
                val restored = cached.copy(
                    phoneNumber = fullPhone,
                    displayName = remote?.displayName?.takeIf { it.isNotBlank() } ?: cached.displayName.ifBlank { fullPhone },
                    about = remote?.about?.takeIf { it.isNotBlank() } ?: cached.about,
                    avatarBase64 = remote?.avatarBase64 ?: cached.avatarBase64,
                    isRegistered = true
                )
                prefs.save(restored)
                _profile.value = restored
                _isAuthLoading.value = false
                _onboardingStep.value = OnboardingStep.DONE
                syncKeysAndPush(restored.serverHost, restored.phoneNumber)
                socket.start(viewModelScope, restored.phoneNumber, restored.serverHost)
                refreshStatuses()
                return@launch
            }

            if (_profile.value.phoneNumber.isNotBlank() && _profile.value.phoneNumber != fullPhone) {
                dao.clearAllMessages()
                dao.clearAllChats()
            }

            val res = socket.requestOtp(server, fullPhone, cleanEmail)
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
        val email = _pendingEmail.value
        _otpError.value = null
        _isAuthLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val server = _profile.value.serverHost
            val res = socket.verifyOtp(server, phone, code.trim())
            if (res.ok) {
                prefs.saveVerifiedIdentity(phone, email)
                val existingRemote = socket.fetchProfile(server, phone)
                _isAuthLoading.value = false
                if (existingRemote != null && existingRemote.displayName.isNotBlank() && existingRemote.displayName != phone) {
                    val restored = _profile.value.copy(
                        phoneNumber = phone,
                        displayName = existingRemote.displayName,
                        about = existingRemote.about,
                        avatarBase64 = existingRemote.avatarBase64,
                        isRegistered = true
                    )
                    prefs.save(restored)
                    _profile.value = restored
                    _onboardingStep.value = OnboardingStep.DONE
                    syncKeysAndPush(restored.serverHost, restored.phoneNumber)
                    socket.start(viewModelScope, restored.phoneNumber, restored.serverHost)
                    refreshStatuses()
                } else {
                    _onboardingStep.value = OnboardingStep.PROFILE
                }
            } else {
                _isAuthLoading.value = false
                _otpError.value = res.message
            }
        }
    }

    fun completeProfile(name: String, about: String, avatarUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val avatarB64 = avatarUri?.let { MediaHelper.uriToCompressedBase64(appContext, it, 320) }
            val updated = _profile.value.copy(
                phoneNumber = _pendingPhone.value,
                displayName = name.trim().ifBlank { _pendingPhone.value },
                about = about.trim().ifBlank { "Hey there! I am using GlobalChat." },
                avatarBase64 = avatarB64,
                isRegistered = true
            )
            prefs.save(updated)
            prefs.saveVerifiedIdentity(_pendingPhone.value, _pendingEmail.value)
            _profile.value = updated
            _onboardingStep.value = OnboardingStep.DONE
            socket.syncProfile(updated.serverHost, updated.phoneNumber, updated.displayName, updated.about, updated.avatarBase64)
            syncKeysAndPush(updated.serverHost, updated.phoneNumber)
            socket.start(viewModelScope, updated.phoneNumber, updated.serverHost)
            refreshStatuses()
        }
    }

    fun updateProfileAndServer(
        name: String,
        about: String,
        serverHost: String,
        readReceipts: Boolean,
        themeMode: String,
        avatarUri: Uri?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _profile.value
            val avatarB64 = if (avatarUri != null) MediaHelper.uriToCompressedBase64(appContext, avatarUri, 320) else current.avatarBase64
            val updated = current.copy(
                displayName = name.trim().ifBlank { current.displayName },
                about = about.trim().ifBlank { current.about },
                serverHost = serverHost.trim().ifBlank { current.serverHost },
                readReceipts = readReceipts,
                themeMode = themeMode,
                avatarBase64 = avatarB64
            )
            prefs.save(updated)
            _profile.value = updated
            socket.syncProfile(updated.serverHost, updated.phoneNumber, updated.displayName, updated.about, updated.avatarBase64)
            syncKeysAndPush(updated.serverHost, updated.phoneNumber)
            socket.start(viewModelScope, updated.phoneNumber, updated.serverHost)
        }
    }

    fun logOut() {
        rtcManager.close()
        socket.stop()
        prefs.clearSessionOnly()
        _activeChatId.value = null
        _activeCall.value = null
        _onboardingStep.value = OnboardingStep.WELCOME
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
                if (reg != null) c.copy(isOnGlobalChat = true, about = reg.about, avatarBase64 = reg.avatarBase64)
                else c
            }.sortedByDescending { it.isOnGlobalChat }

            registered.values.forEach { reg ->
                dao.getChat(reg.phone)?.let { existing ->
                    if (reg.avatarBase64 != null && existing.avatarBase64 != reg.avatarBase64) {
                        dao.upsertChat(existing.copy(avatarBase64 = reg.avatarBase64))
                    }
                }
            }
        }
    }

    fun startChat(partnerPhone: String, partnerName: String? = null, avatarBase64: String? = null) {
        val clean = partnerPhone.replace(Regex("[\\s()-]"), "")
        val me = _profile.value.phoneNumber
        if (clean.isBlank() || clean == me) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getChat(clean)
            if (existing == null) {
                val remote = if (avatarBase64 == null) socket.fetchProfile(_profile.value.serverHost, clean) else null
                dao.upsertChat(
                    ChatEntity(
                        chatId = clean,
                        title = partnerName?.takeIf { it.isNotBlank() } ?: remote?.displayName?.takeIf { it.isNotBlank() } ?: clean,
                        isGroup = false,
                        memberIdsCsv = clean,
                        lastMessage = "Tap to start chatting",
                        lastTimestamp = System.currentTimeMillis(),
                        avatarBase64 = avatarBase64 ?: remote?.avatarBase64
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
            dao.upsertChat(ChatEntity(groupId, groupName.trim(), true, members.joinToString(","), "You created group \"${groupName.trim()}\"", System.currentTimeMillis()))
            openChat(groupId)
        }
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearUnread(chatId)
            val chat = dao.getChat(chatId) ?: return@launch
            if (!chat.isGroup && chat.avatarBase64 == null) {
                socket.fetchProfile(_profile.value.serverHost, chatId)?.let { remote ->
                    dao.upsertChat(chat.copy(
                        title = if (chat.title == chatId && remote.displayName.isNotBlank()) remote.displayName else chat.title,
                        avatarBase64 = remote.avatarBase64
                    ))
                }
            }
            if (!_profile.value.readReceipts) return@launch
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
        val server = _profile.value.serverHost
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val chat = dao.getChat(chatId) ?: return@launch
            val receivers = chat.memberIdsCsv.split(",").filter { it.isNotBlank() && it != me }
            val msgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            dao.upsertMessage(MessageEntity(msgId, chatId, me, content, now, MessageStatus.SENDING, mediaType, mediaBase64, durationSec, replyTo?.id, replyTo?.content))
            dao.upsertChat(chat.copy(lastMessage = content, lastTimestamp = now))

            var wireContent = content
            var wireMedia = mediaBase64
            var wireReply = replyTo?.content
            var encryptedFlag = false

            if (!chat.isGroup && receivers.size == 1) {
                val partnerKey = socket.fetchPublicKey(server, receivers.first())
                if (!partnerKey.isNullOrBlank()) {
                    val encC = E2eeCrypto.encrypt(appContext, content, partnerKey)
                    if (encC != null) {
                        wireContent = encC
                        wireMedia = mediaBase64?.let { E2eeCrypto.encrypt(appContext, it, partnerKey) } ?: mediaBase64
                        wireReply = replyTo?.content?.let { E2eeCrypto.encrypt(appContext, it, partnerKey) } ?: replyTo?.content
                        encryptedFlag = true
                    }
                }
            }

            socket.send(
                ChatPayload(
                    id = msgId,
                    chatId = chatId,
                    senderId = me,
                    receiverIds = receivers,
                    content = wireContent,
                    timestamp = now,
                    status = MessageStatus.SENDING,
                    type = "TEXT",
                    mediaType = mediaType,
                    mediaBase64 = wireMedia,
                    durationSec = durationSec,
                    replyToId = replyTo?.id,
                    replyToText = wireReply,
                    isGroup = chat.isGroup,
                    groupName = if (chat.isGroup) chat.title else null,
                    isEncrypted = encryptedFlag
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
            val p = _profile.value
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            dao.insertStatus(StatusEntity(id, "My status", p.phoneNumber, p.avatarBase64, caption.trim(), img, bgColorHex, now))
            socket.postStatus(
                p.serverHost,
                StatusDto(id, p.phoneNumber, p.displayName.ifBlank { p.phoneNumber }, p.avatarBase64, caption.trim(), img, bgColorHex, now)
            )
        }
    }

    fun refreshStatuses() {
        val me = _profile.value.phoneNumber
        if (me.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            socket.fetchStatuses(_profile.value.serverHost).forEach { s ->
                val existing = dao.getStatus(s.id)
                val displayOwner = if (s.ownerPhone == me) "My status" else s.ownerName
                dao.insertStatus(
                    StatusEntity(
                        id = s.id,
                        ownerName = displayOwner,
                        ownerId = s.ownerPhone,
                        ownerAvatarBase64 = s.ownerAvatar,
                        caption = s.caption,
                        imageBase64 = s.imageBase64,
                        bgColorHex = s.bgColorHex,
                        timestamp = s.timestamp,
                        viewed = existing?.viewed ?: (s.ownerPhone == me)
                    )
                )
            }
        }
    }

    fun markStatusViewed(id: String) { viewModelScope.launch { dao.markStatusViewed(id) } }

    fun startCall(partnerId: String, title: String, isVideo: Boolean) {
        viewModelScope.launch {
            val avatar = dao.getChat(partnerId)?.avatarBase64
            _activeCall.value = ActiveCallUiState(partnerId, title, isVideo, CallStatus.OUTGOING_RINGING, isMuted = false, isSpeaker = isVideo, partnerAvatarBase64 = avatar)
            dao.insertCall(CallLogEntity(UUID.randomUUID().toString(), partnerId, title, System.currentTimeMillis(), isVideo))
            rtcManager.startOutgoingCall(isVideo)
        }
    }

    fun acceptIncomingCall() {
        cancelCallNotification()
        val call = _activeCall.value ?: return
        val offerSdp = pendingIncomingOfferSdp ?: return
        _activeCall.value = call.copy(status = CallStatus.CONNECTING)
        rtcManager.answerIncomingCall(offerSdp, call.isVideo)
    }

    fun toggleCallMute() {
        _activeCall.update { current ->
            if (current == null) null else {
                val next = !current.isMuted
                rtcManager.setMuted(next)
                current.copy(isMuted = next)
            }
        }
    }

    fun toggleCallSpeaker() {
        _activeCall.update { current ->
            if (current == null) null else {
                val next = !current.isSpeaker
                rtcManager.setSpeakerphone(next)
                current.copy(isSpeaker = next)
            }
        }
    }

    fun attachCallRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) = rtcManager.attachRenderers(local, remote)
    fun detachCallRenderers() = rtcManager.detachRenderers()

    fun endCall() = endCall(sendSignal = true)

    fun endCall(sendSignal: Boolean) {
        cancelCallNotification()
        val current = _activeCall.value
        if (sendSignal && current != null) {
            val me = _profile.value.phoneNumber
            viewModelScope.launch {
                socket.send(ChatPayload(UUID.randomUUID().toString(), current.partnerId, me, listOf(current.partnerId), "", System.currentTimeMillis(), type = "CALL_END"))
            }
        }
        pendingIncomingOfferSdp = null
        rtcManager.close()
        _activeCall.value = null
    }

    private fun cancelCallNotification() {
        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(9001)
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        rtcManager.close()
    }

    private suspend fun handleIncoming(payload: ChatPayload) {
        val me = _profile.value.phoneNumber
        val server = _profile.value.serverHost
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
            "CALL_OFFER" -> {
                pendingIncomingOfferSdp = payload.content
                val localChat = dao.getChat(payload.senderId)
                val remoteUser = if (localChat?.avatarBase64 == null) socket.fetchProfile(server, payload.senderId) else null
                val title = localChat?.title ?: remoteUser?.displayName?.ifBlank { payload.senderId } ?: payload.senderId
                val avatar = localChat?.avatarBase64 ?: remoteUser?.avatarBase64
                dao.insertCall(CallLogEntity(UUID.randomUUID().toString(), payload.senderId, title, System.currentTimeMillis(), payload.callVideo, isIncoming = true))
                _activeCall.value = ActiveCallUiState(payload.senderId, title, payload.callVideo, CallStatus.INCOMING_RINGING, isMuted = false, isSpeaker = payload.callVideo, partnerAvatarBase64 = avatar)
            }
            "CALL_ANSWER" -> {
                _activeCall.update { it?.copy(status = CallStatus.CONNECTING) }
                rtcManager.onRemoteAnswer(payload.content)
            }
            "CALL_ICE" -> rtcManager.addRemoteIceCandidate(IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.content))
            "CALL_END" -> endCall(sendSignal = false)
            "TEXT" -> {
                var plainContent = payload.content
                var plainMedia = payload.mediaBase64
                var plainReply = payload.replyToText

                if (payload.isEncrypted) {
                    val senderKey = socket.fetchPublicKey(server, payload.senderId)
                    plainContent = if (!senderKey.isNullOrBlank()) {
                        E2eeCrypto.decrypt(appContext, payload.content, senderKey) ?: "🔒 Unable to decrypt message"
                    } else "🔒 Encrypted message"
                    plainMedia = if (!senderKey.isNullOrBlank() && payload.mediaBase64 != null) {
                        E2eeCrypto.decrypt(appContext, payload.mediaBase64, senderKey)
                    } else payload.mediaBase64
                    plainReply = if (!senderKey.isNullOrBlank() && payload.replyToText != null) {
                        E2eeCrypto.decrypt(appContext, payload.replyToText, senderKey)
                    } else payload.replyToText
                }

                val existingChat = dao.getChat(resolvedChatId)
                val remoteProfile = if (!payload.isGroup && existingChat?.avatarBase64 == null) {
                    socket.fetchProfile(server, payload.senderId)
                } else null
                val resolvedTitle = payload.groupName
                    ?: existingChat?.title?.takeIf { it != payload.senderId }
                    ?: remoteProfile?.displayName?.takeIf { it.isNotBlank() }
                    ?: payload.senderId
                val resolvedAvatar = existingChat?.avatarBase64 ?: remoteProfile?.avatarBase64

                val isOpen = _activeChatId.value == resolvedChatId
                val unread = if (isOpen) 0 else (existingChat?.unreadCount ?: 0) + 1
                val membersCsv = if (payload.isGroup) (payload.receiverIds + payload.senderId).distinct().joinToString(",") else payload.senderId
                dao.upsertChat(ChatEntity(resolvedChatId, resolvedTitle, payload.isGroup, membersCsv, plainContent, payload.timestamp, unread, resolvedAvatar))
                dao.upsertMessage(MessageEntity(payload.id, resolvedChatId, payload.senderId, plainContent, payload.timestamp, MessageStatus.DELIVERED, payload.mediaType, plainMedia, payload.durationSec, payload.replyToId, plainReply))
                if (isOpen && _profile.value.readReceipts) {
                    socket.send(payload.copy(id = UUID.randomUUID().toString(), senderId = me, receiverIds = listOf(payload.senderId), type = "ACK_READ"))
                }
            }
        }
    }
}