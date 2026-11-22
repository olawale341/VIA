package com.globalchat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.model.*
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallScreen(
    call: ActiveCallUiState,
    eglContext: EglBase.Context,
    getAvatarUrl: (String, Long) -> String?,
    onAttachRenderers: (SurfaceViewRenderer?, SurfaceViewRenderer?) -> Unit,
    onDetachRenderers: () -> Unit,
    onAccept: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var seconds by remember(call.status) { mutableIntStateOf(0) }

    LaunchedEffect(call.status) {
        if (call.status == CallStatus.CONNECTED) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    val remoteView = remember(call.isVideo) {
        if (!call.isVideo) null else SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    val localView = remember(call.isVideo) {
        if (!call.isVideo) null else SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setZOrderMediaOverlay(true)
            setMirror(true)
            setEnableHardwareScaler(true)
        }
    }

    DisposableEffect(call.isVideo) {
        if (call.isVideo) onAttachRenderers(localView, remoteView)
        onDispose { if (call.isVideo) onDetachRenderers() }
    }

    val statusText = when (call.status) {
        CallStatus.OUTGOING_RINGING -> if (call.isVideo) "Video calling..." else "Calling..."
        CallStatus.INCOMING_RINGING -> if (call.isVideo) "Incoming video call..." else "Incoming voice call..."
        CallStatus.CONNECTING -> "Connecting..."
        CallStatus.CONNECTED -> "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141A))) {
        if (call.isVideo && remoteView != null) {
            AndroidView(factory = { remoteView }, modifier = Modifier.fillMaxSize())
        }

        if (call.isVideo && localView != null) {
            AndroidView(
                factory = { localView },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(width = 105.dp, height = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text("🔒 End-to-end encrypted", fontSize = 12.sp, color = Color(0xFF8696A0))
                Spacer(Modifier.height(18.dp))
                Text(call.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(statusText, fontSize = 15.sp, color = Color(0xFFD9FDD3))
            }

            if (!call.isVideo || call.status != CallStatus.CONNECTED) {
                AvatarImage(
                    avatarUrl = getAvatarUrl(call.partnerId, call.partnerAvatarVersion),
                    avatarBase64 = call.partnerAvatarBase64,
                    size = 134.dp
                )
            } else {
                Spacer(Modifier.height(134.dp))
            }

            if (call.status == CallStatus.INCOMING_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onEndCall, modifier = Modifier.size(66.dp).background(Color(0xFFE53935), CircleShape)) {
                            Icon(Icons.Default.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Decline", color = Color.White, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onAccept, modifier = Modifier.size(66.dp).background(Color(0xFF25D366), CircleShape)) {
                            Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call, "Accept", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Accept", color = Color.White, fontSize = 12.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.size(54.dp).background(if (call.isSpeaker) Color.White else Color(0xFF202C33), CircleShape)
                    ) {
                        Icon(Icons.Default.VolumeUp, "Speaker", tint = if (call.isSpeaker) Color.Black else Color.White)
                    }
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(54.dp).background(if (call.isMuted) Color.White else Color(0xFF202C33), CircleShape)
                    ) {
                        Icon(if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", tint = if (call.isMuted) Color.Black else Color.White)
                    }
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier.size(64.dp).background(Color(0xFFE53935), CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chat: ChatEntity,
    members: List<RegisteredUserDto>,
    getAvatarUrl: (String, Long) -> String?,
    onBack: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit
) {
    val p = LocalWaPalette.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val adminList = remember(chat.adminIdsCsv) { chat.adminIdsCsv.split(",").map { it.trim() }.toSet() }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary) }
                },
                title = { Text(if (chat.isGroup) "Group info" else "Contact info", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.topBar)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarImage(
                        avatarUrl = if (chat.isGroup) null else getAvatarUrl(chat.chatId, chat.avatarVersion),
                        avatarBase64 = chat.avatarBase64,
                        isGroup = chat.isGroup,
                        size = 110.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(chat.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (chat.isGroup) "Group • ${members.size.coerceAtLeast(1)} participants" else chat.chatId,
                        fontSize = 14.sp,
                        color = p.textSecondary
                    )
                    if (!chat.isGroup) {
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            OutlinedButton(onClick = { onStartCall(false) }) {
                                Icon(Icons.Default.Call, null, tint = p.accent)
                                Spacer(Modifier.width(6.dp))
                                Text("Audio", color = p.accent)
                            }
                            OutlinedButton(onClick = { onStartCall(true) }) {
                                Icon(Icons.Default.Videocam, null, tint = p.accent)
                                Spacer(Modifier.width(6.dp))
                                Text("Video", color = p.accent)
                            }
                        }
                    }
                }
                HorizontalDivider(color = p.divider, thickness = 8.dp)
            }

            if (!chat.isGroup && members.isNotEmpty()) {
                val u = members.first()
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("About", fontSize = 13.sp, color = p.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(u.about, fontSize = 15.5.sp, color = p.textPrimary)
                    }
                    HorizontalDivider(color = p.divider, thickness = 8.dp)
                }
            }

            if (chat.isGroup) {
                item {
                    Text(
                        "${members.size} participants",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.textSecondary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
                items(members, key = { it.phone }) { member ->
                    val isAdmin = adminList.contains(member.phone)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            AvatarImage(
                                avatarUrl = getAvatarUrl(member.phone, member.avatarVersion),
                                avatarBase64 = member.avatarBase64,
                                size = 44.dp
                            )
                        },
                        headlineContent = { Text(member.displayName.ifBlank { member.phone }, color = p.textPrimary, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(member.about, color = p.textSecondary, fontSize = 12.5.sp, maxLines = 1) },
                        trailingContent = {
                            if (isAdmin) {
                                Surface(color = p.accentPill, shape = RoundedCornerShape(6.dp)) {
                                    Text("Group Admin", fontSize = 11.sp, color = p.accentPillIcon, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    )
                }
                item { HorizontalDivider(color = p.divider, thickness = 8.dp) }
            }

            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Default.CleaningServices, null, tint = p.danger) },
                    headlineContent = { Text("Clear chat", color = p.danger, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.clickable { showClearConfirm = true }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = p.danger) },
                    headlineContent = { Text("Delete chat", color = p.danger, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.clickable { showDeleteConfirm = true }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = p.surface,
            title = { Text("Clear this chat?", color = p.textPrimary) },
            text = { Text("Messages will be cleared from your copy of this conversation only.", color = p.textSecondary) },
            confirmButton = { TextButton(onClick = { showClearConfirm = false; onClearChat(); onBack() }) { Text("Clear", color = p.danger) } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = p.accent) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = p.surface,
            title = { Text("Delete this chat?", color = p.textPrimary) },
            text = { Text("This deletes the chat from your device only. Other participants keep their messages.", color = p.textSecondary) },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDeleteChat() }) { Text("Delete", color = p.danger) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = p.accent) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    uploadStatus: UploadStatusUi,
    getAvatarUrl: (String, Long) -> String?,
    onClearUploadStatus: () -> Unit,
    onSave: (String, String, String, Boolean, String, Uri?, () -> Unit) -> Unit,
    onBack: () -> Unit,
    onLogOut: () -> Unit
) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    var name by remember { mutableStateOf(profile.displayName) }
    var about by remember { mutableStateOf(profile.about) }
    var serverHost by remember { mutableStateOf(profile.serverHost) }
    var readReceipts by remember { mutableStateOf(profile.readReceipts) }
    var themeMode by remember { mutableStateOf(profile.themeMode) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) {
            pickedUri = it
            onClearUploadStatus()
        }
    }
    val previewBmp = remember(pickedUri) {
        pickedUri?.let { MediaHelper.uriToCompressedBase64(context, it, 300)?.let(MediaHelper::decodeBase64ToImage) }
    }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onClearUploadStatus(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary) }
                },
                title = { Text("Settings", color = p.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.topBar)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(74.dp).clip(CircleShape).background(p.surface).clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    Alignment.Center
                ) {
                    if (previewBmp != null) {
                        Image(previewBmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        AvatarImage(
                            avatarUrl = getAvatarUrl(profile.phoneNumber, profile.avatarVersion),
                            avatarBase64 = profile.avatarBase64,
                            size = 74.dp
                        )
                    }
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(p.accent).align(Alignment.BottomEnd),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, tint = p.accentText, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = p.textPrimary)
                    Text(profile.phoneNumber, fontSize = 13.sp, color = p.textSecondary)
                }
            }

            if (uploadStatus.isUploading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { uploadStatus.progress }, color = p.accent, modifier = Modifier.fillMaxWidth())
                    Text(uploadStatus.message ?: "Uploading...", fontSize = 12.5.sp, color = p.textSecondary)
                }
            } else if (uploadStatus.message != null) {
                Surface(
                    color = if (uploadStatus.isError) p.danger.copy(alpha = 0.15f) else p.accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        uploadStatus.message,
                        color = if (uploadStatus.isError) p.danger else p.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = p.textPrimary, unfocusedTextColor = p.textPrimary, focusedBorderColor = p.accent, unfocusedBorderColor = p.surfaceVariant, focusedLabelColor = p.accent, unfocusedLabelColor = p.textSecondary),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = p.textPrimary, unfocusedTextColor = p.textPrimary, focusedBorderColor = p.accent, unfocusedBorderColor = p.surfaceVariant, focusedLabelColor = p.accent, unfocusedLabelColor = p.textSecondary),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("Cloud Server Address") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = p.textPrimary, unfocusedTextColor = p.textPrimary, focusedBorderColor = p.accent, unfocusedBorderColor = p.surfaceVariant, focusedLabelColor = p.accent, unfocusedLabelColor = p.textSecondary),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Theme", fontWeight = FontWeight.Medium, color = p.textPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                    FilterChip(
                        selected = themeMode == key,
                        onClick = { themeMode = key },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Read receipts", fontWeight = FontWeight.Medium, color = p.textPrimary)
                    Text("Send and receive blue ticks", fontSize = 12.sp, color = p.textSecondary)
                }
                Switch(checked = readReceipts, onCheckedChange = { readReceipts = it })
            }

            Button(
                onClick = {
                    onSave(name, about, serverHost, readReceipts, themeMode, pickedUri) {
                        onBack()
                    }
                },
                enabled = !uploadStatus.isUploading,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (uploadStatus.isUploading) "Uploading..." else "Save Changes", color = p.accentText, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(onClick = { showLogoutConfirm = true }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Log Out / Switch Number", color = p.danger)
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = p.surface,
            title = { Text("Log out of GlobalChat?", color = p.textPrimary) },
            text = { Text("You can log straight back in anytime using your same phone number and email.", color = p.textSecondary) },
            confirmButton = { TextButton(onClick = { showLogoutConfirm = false; onLogOut() }) { Text("Log out", color = p.danger) } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = p.accent) } }
        )
    }
}