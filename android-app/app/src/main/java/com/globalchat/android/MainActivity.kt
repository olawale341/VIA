package com.globalchat.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.globalchat.android.model.OnboardingStep
import com.globalchat.android.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GlobalChatApp()
            }
        }
    }
}

@Composable
fun GlobalChatApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val step by vm.onboardingStep.collectAsState()
    val profile by vm.profile.collectAsState()
    val pendingPhone by vm.pendingPhone.collectAsState()
    val pendingEmail by vm.pendingEmail.collectAsState()
    val otpError by vm.otpError.collectAsState()
    val isAuthLoading by vm.isAuthLoading.collectAsState()
    val activeChatId by vm.activeChatId.collectAsState()
    val activeCall by vm.activeCall.collectAsState()
    val chats by vm.chats.collectAsState()
    val messages by vm.activeMessages.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val onlineUsers by vm.onlineUsers.collectAsState()
    val typingUsers by vm.typingUsers.collectAsState()
    val phoneContacts by vm.phoneContacts.collectAsState()
    val statuses by vm.statuses.collectAsState()
    val callLogs by vm.callLogs.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            pendingCallAction?.invoke()
        }
        pendingCallAction = null
    }

    fun runWithCallPermissions(isVideo: Boolean, action: () -> Unit) {
        val needed = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        val allGranted = needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            action()
        } else {
            pendingCallAction = action
            callPermLauncher.launch(needed)
        }
    }

    when (step) {
        OnboardingStep.WELCOME -> WelcomeScreen(onAgree = vm::acceptTerms)
        OnboardingStep.PHONE -> PhoneEntryScreen(
            isLoading = isAuthLoading,
            errorText = otpError,
            onSubmit = vm::submitPhone
        )
        OnboardingStep.OTP -> OtpVerificationScreen(
            phoneNumber = pendingPhone,
            email = pendingEmail,
            isLoading = isAuthLoading,
            errorText = otpError,
            onVerify = vm::verifyOtp,
            onResend = vm::resendOtp,
            onWrongNumber = vm::backToPhone
        )
        OnboardingStep.PROFILE -> ProfileSetupScreen(onComplete = vm::completeProfile)
        OnboardingStep.DONE -> {
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val call = activeCall
            if (call != null) {
                BackHandler { vm.endCall() }
                ActiveCallScreen(
                    call = call,
                    eglContext = vm.rtcManager.eglBase.eglBaseContext,
                    onAttachRenderers = vm::attachCallRenderers,
                    onDetachRenderers = vm::detachCallRenderers,
                    onAccept = { runWithCallPermissions(call.isVideo) { vm.acceptIncomingCall() } },
                    onToggleMute = vm::toggleCallMute,
                    onToggleSpeaker = vm::toggleCallSpeaker,
                    onEndCall = { vm.endCall() }
                )
            } else if (showSettings) {
                BackHandler { showSettings = false }
                SettingsScreen(
                    profile = profile,
                    onSave = vm::updateProfileAndServer,
                    onBack = { showSettings = false },
                    onLogOut = { showSettings = false; vm.logOut() }
                )
            } else if (activeChatId != null) {
                BackHandler { vm.closeChat() }
                val currentChat = chats.find { it.chatId == activeChatId }
                if (currentChat != null) {
                    ChatScreen(
                        chat = currentChat,
                        myUserId = profile.phoneNumber,
                        isPartnerOnline = onlineUsers.contains(currentChat.chatId),
                        isPartnerTyping = typingUsers.any { currentChat.memberIdsCsv.contains(it) },
                        messages = messages,
                        onBack = vm::closeChat,
                        onSendText = vm::sendTextMessage,
                        onSendImage = vm::sendImageMessage,
                        onSendVoice = vm::sendVoiceMessage,
                        onTypingChanged = vm::sendTyping,
                        onReact = vm::sendReaction,
                        onDelete = vm::deleteMessageForEveryone,
                        onStartCall = { isVideo ->
                            runWithCallPermissions(isVideo) {
                                vm.startCall(currentChat.chatId, currentChat.title, isVideo)
                            }
                        }
                    )
                }
            } else {
                HomeScreen(
                    profile = profile,
                    isConnected = isConnected,
                    chats = chats,
                    onlineUsers = onlineUsers,
                    phoneContacts = phoneContacts,
                    statuses = statuses,
                    callLogs = callLogs,
                    onRequestLoadContacts = vm::loadDeviceContacts,
                    onChatClick = vm::openChat,
                    onStartNewChat = vm::startChat,
                    onCreateGroup = vm::createGroup,
                    onPostStatus = vm::postStatus,
                    onStatusViewed = vm::markStatusViewed,
                    onStartCall = { partnerId, title, isVideo ->
                        runWithCallPermissions(isVideo) {
                            vm.startCall(partnerId, title, isVideo)
                        }
                    },
                    onOpenSettings = { showSettings = true },
                    onLogOut = vm::logOut
                )
            }
        }
    }
}