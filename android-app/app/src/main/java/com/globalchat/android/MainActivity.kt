package com.globalchat.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.globalchat.android.model.OnboardingStep
import com.globalchat.android.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val profile by vm.profile.collectAsState()

            GlobalChatTheme(themeMode = profile.themeMode) {
                val p = LocalWaPalette.current
                LaunchedEffect(p.isDark) {
                    val barColor = p.topBar.toArgb()
                    val style = if (p.isDark) SystemBarStyle.dark(barColor) else SystemBarStyle.light(barColor, barColor)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    GlobalChatApp(vm)
                }
            }
        }
    }
}

@Composable
fun GlobalChatApp(vm: MainViewModel) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    val step by vm.onboardingStep.collectAsState()
    val profile by vm.profile.collectAsState()
    val pendingPhone by vm.pendingPhone.collectAsState()
    val pendingEmail by vm.pendingEmail.collectAsState()
    val otpError by vm.otpError.collectAsState()
    val isAuthLoading by vm.isAuthLoading.collectAsState()
    val uploadStatus by vm.uploadStatus.collectAsState()
    val actionError by vm.actionError.collectAsState()
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
    val chatInfoMembers by vm.chatInfoMembers.collectAsState()
    val memberProfiles by vm.memberProfiles.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showChatInfo by remember { mutableStateOf(false) }
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(activeChatId) {
        if (activeChatId == null) showChatInfo = false
    }

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
                    getAvatarUrl = vm::getAvatarUrl,
                    onAttachRenderers = vm::attachCallRenderers,
                    onDetachRenderers = vm::detachCallRenderers,
                    onAccept = { runWithCallPermissions(call.isVideo) { vm.acceptIncomingCall() } },
                    onToggleMute = vm::toggleCallMute,
                    onToggleSpeaker = vm::toggleCallSpeaker,
                    onEndCall = { vm.endCall() }
                )
            } else if (showSettings) {
                BackHandler { vm.clearUploadStatus(); showSettings = false }
                SettingsScreen(
                    profile = profile,
                    uploadStatus = uploadStatus,
                    getAvatarUrl = vm::getAvatarUrl,
                    onClearUploadStatus = vm::clearUploadStatus,
                    onSave = vm::updateProfileAndServer,
                    onBack = { vm.clearUploadStatus(); showSettings = false },
                    onLogOut = { vm.clearUploadStatus(); showSettings = false; vm.logOut() }
                )
            } else if (activeChatId != null) {
                val currentChat = chats.find { it.chatId == activeChatId }
                if (currentChat != null) {
                    if (showChatInfo) {
                        BackHandler { showChatInfo = false }
                        ChatInfoScreen(
                            chat = currentChat,
                            members = chatInfoMembers,
                            getAvatarUrl = vm::getAvatarUrl,
                            onBack = { showChatInfo = false },
                            onStartCall = { isVideo ->
                                runWithCallPermissions(isVideo) {
                                    vm.startCall(currentChat.chatId, currentChat.title, isVideo)
                                }
                            },
                            onClearChat = { vm.clearChat(currentChat.chatId) },
                            onDeleteChat = { showChatInfo = false; vm.deleteEntireChat(currentChat.chatId) }
                        )
                    } else {
                        BackHandler { vm.closeChat() }
                        ChatScreen(
                            chat = currentChat,
                            myUserId = profile.phoneNumber,
                            myAvatarBase64 = profile.avatarBase64,
                            myAvatarVersion = profile.avatarVersion,
                            isPartnerOnline = onlineUsers.contains(currentChat.chatId),
                            isPartnerTyping = typingUsers.any { currentChat.memberIdsCsv.contains(it) },
                            messages = messages,
                            memberProfiles = memberProfiles,
                            getAvatarUrl = vm::getAvatarUrl,
                            canDeleteForEveryone = { selected -> vm.canDeleteForEveryone(selected, currentChat) },
                            onBack = vm::closeChat,
                            onOpenInfo = {
                                vm.loadChatInfo(currentChat.chatId)
                                showChatInfo = true
                            },
                            onSendText = vm::sendTextMessage,
                            onSendImage = vm::sendImageMessage,
                            onSendVoice = vm::sendVoiceMessage,
                            onTypingChanged = vm::sendTyping,
                            onReact = vm::sendReaction,
                            onDeleteForMe = vm::deleteMessagesForMe,
                            onDeleteForEveryone = vm::deleteMessagesForEveryone,
                            onClearChat = { vm.clearChat(currentChat.chatId) },
                            onDeleteChat = { vm.deleteEntireChat(currentChat.chatId) },
                            onStartCall = { isVideo ->
                                runWithCallPermissions(isVideo) {
                                    vm.startCall(currentChat.chatId, currentChat.title, isVideo)
                                }
                            }
                        )
                    }
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
                    getAvatarUrl = vm::getAvatarUrl,
                    onRequestLoadContacts = vm::loadDeviceContacts,
                    onRefreshStatuses = vm::refreshStatuses,
                    onChatClick = vm::openChat,
                    onDeleteChat = vm::deleteEntireChat,
                    onStartNewChat = vm::startChat,
                    onCreateGroup = vm::createGroup,
                    onPostStatus = vm::postStatus,
                    onStatusViewed = vm::markStatusViewed,
                    onStartCall = { partnerId, title, isVideo ->
                        runWithCallPermissions(isVideo) {
                            vm.startCall(partnerId, title, isVideo)
                        }
                    },
                    onOpenSettings = { vm.clearUploadStatus(); showSettings = true },
                    onLogOut = vm::logOut
                )
            }
        }
    }

    actionError?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearActionError,
            containerColor = p.surface,
            title = { Text("Action not allowed", color = p.textPrimary) },
            text = { Text(err, color = p.textSecondary) },
            confirmButton = {
                TextButton(onClick = vm::clearActionError) { Text("OK", color = p.accent) }
            }
        )
    }
}