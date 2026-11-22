package com.globalchat.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.data.local.MessageEntity
import com.globalchat.android.model.MessageStatus
import com.globalchat.android.model.RegisteredUserDto
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chat: ChatEntity,
    myUserId: String,
    myAvatarBase64: String?,
    myAvatarVersion: Long,
    isPartnerOnline: Boolean,
    isPartnerTyping: Boolean,
    messages: List<MessageEntity>,
    memberProfiles: Map<String, RegisteredUserDto>,
    getAvatarUrl: (String, Long) -> String?,
    canDeleteForEveryone: (List<MessageEntity>) -> Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onSendText: (String, MessageEntity?) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReact: (String, String) -> Unit,
    onDeleteForMe: (List<String>) -> Unit,
    onDeleteForEveryone: (List<String>) -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReactionDialogFor by remember { mutableStateOf<MessageEntity?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }
    var viewingPhotoMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var showEmojiRow by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isSelectionMode = selectedIds.isNotEmpty()
    val selectedMessages = remember(selectedIds, messages) {
        messages.filter { selectedIds.contains(it.id) }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptySet()
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendImage(uri)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) isRecording = MediaHelper.startVoiceRecording(context)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            onTypingChanged(true)
            delay(1500)
            onTypingChanged(false)
        } else {
            onTypingChanged(false)
        }
    }

    Scaffold(
        containerColor = p.chatBg,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel Selection", tint = p.textPrimary)
                        }
                    },
                    title = {
                        Text("${selectedMessages.size}", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = p.textPrimary)
                    },
                    actions = {
                        if (selectedMessages.size == 1) {
                            val single = selectedMessages.first()
                            IconButton(onClick = {
                                replyingTo = single
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Reply, "Reply", tint = p.textPrimary)
                            }
                            IconButton(onClick = {
                                showReactionDialogFor = single
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.AddReaction, "React", tint = p.textPrimary)
                            }
                        }
                        if (selectedMessages.any { !it.isDeleted && it.mediaType == "TEXT" }) {
                            IconButton(onClick = {
                                val copied = selectedMessages
                                    .filter { !it.isDeleted && it.mediaType == "TEXT" }
                                    .joinToString("\n") { it.content }
                                clipboard.setText(AnnotatedString(copied))
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = p.textPrimary)
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = p.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface)
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary)
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenInfo() }
                        ) {
                            AvatarImage(
                                avatarUrl = if (chat.isGroup) null else getAvatarUrl(chat.chatId, chat.avatarVersion),
                                avatarBase64 = chat.avatarBase64,
                                isGroup = chat.isGroup,
                                size = 38.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(chat.title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                                val sub = when {
                                    isPartnerTyping -> "typing..."
                                    chat.isGroup -> "${chat.memberIdsCsv.split(",").size} participants • tap for info"
                                    isPartnerOnline -> "online"
                                    else -> "tap here for contact info"
                                }
                                Text(sub, fontSize = 12.sp, color = if (isPartnerTyping || isPartnerOnline) p.accent else p.textSecondary)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { onStartCall(true) }) {
                            Icon(Icons.Default.Videocam, "Video Call", tint = p.textPrimary)
                        }
                        IconButton(onClick = { onStartCall(false) }) {
                            Icon(Icons.Default.Call, "Voice Call", tint = p.textPrimary)
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Chat Menu", tint = p.textPrimary)
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }, containerColor = p.surface) {
                                DropdownMenuItem(
                                    text = { Text(if (chat.isGroup) "Group info" else "View contact", color = p.textPrimary) },
                                    onClick = { showOverflowMenu = false; onOpenInfo() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear chat", color = p.textPrimary) },
                                    onClick = { showOverflowMenu = false; showClearConfirm = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete chat", color = p.danger) },
                                    onClick = { showOverflowMenu = false; showDeleteChatConfirm = true }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.topBar)
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(p.chatBg).padding(bottom = 6.dp)) {
                if (showEmojiRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(p.surface).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "🎉", "😢").forEach { e ->
                            TextButton(onClick = { inputText += e }) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
                replyingTo?.let { reply ->
                    val replySenderName = if (reply.senderId == myUserId) "You"
                    else memberProfiles[reply.senderId]?.displayName?.ifBlank { reply.senderId } ?: reply.senderId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .background(p.surface, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).height(30.dp).background(p.accent))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(replySenderName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.accent)
                            Text(reply.content, maxLines = 1, fontSize = 13.sp, color = p.textSecondary)
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Default.Close, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .background(p.surface, RoundedCornerShape(28.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, null, tint = p.danger)
                        Spacer(Modifier.width(8.dp))
                        Text("Recording voice note...", modifier = Modifier.weight(1f), color = p.danger, fontWeight = FontWeight.Medium)
                        IconButton(onClick = {
                            MediaHelper.stopAndEncodeVoiceNote()
                            isRecording = false
                        }) { Icon(Icons.Default.Delete, "Cancel", tint = p.textSecondary) }
                        IconButton(
                            onClick = {
                                val res = MediaHelper.stopAndEncodeVoiceNote()
                                isRecording = false
                                if (res != null) onSendVoice(res.first, res.second)
                            },
                            modifier = Modifier.size(40.dp).background(p.accent, CircleShape)
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send Voice", tint = p.accentText) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .background(p.inBubble, RoundedCornerShape(26.dp))
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showEmojiRow = !showEmojiRow }) {
                                Icon(Icons.Default.EmojiEmotions, "Emoji", tint = p.textSecondary)
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                cursorBrush = SolidColor(p.accent),
                                textStyle = TextStyle(color = p.textPrimary, fontSize = 16.sp),
                                decorationBox = { inner ->
                                    Box(modifier = Modifier.padding(vertical = 10.dp)) {
                                        if (inputText.isEmpty()) Text("Message", color = p.textSecondary, fontSize = 16.sp)
                                        inner()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.AttachFile, "Attach", tint = p.textSecondary)
                            }
                            if (inputText.isEmpty()) {
                                IconButton(onClick = {
                                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) {
                                    Icon(Icons.Default.PhotoCamera, "Photo", tint = p.textSecondary)
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendText(inputText.trim(), replyingTo)
                                    inputText = ""
                                    replyingTo = null
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) isRecording = MediaHelper.startVoiceRecording(context)
                                    else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(48.dp).background(p.accent, CircleShape)
                        ) {
                            Icon(
                                if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = "Send or Voice",
                                tint = p.accentText
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(p.chatBg).padding(padding)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 110.dp.toPx()
                var y = 30f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 30f else 85f
                    while (x < size.width) {
                        drawCircle(p.doodleColor, radius = 16.dp.toPx(), center = Offset(x, y), style = Stroke(2f))
                        drawCircle(p.doodleColor, radius = 6.dp.toPx(), center = Offset(x + 42.dp.toPx(), y + 28.dp.toPx()), style = Stroke(2f))
                        x += step
                    }
                    y += step
                    row++
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                        Surface(color = p.e2eeBg, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                text = if (chat.isGroup) "🔒 Group chat on GlobalChat"
                                else "🔒 Messages and calls are end-to-end encrypted. No one outside of this chat, not even GlobalChat, can read or listen to them.",
                                fontSize = 11.5.sp,
                                color = p.e2eeText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                    val currentDateLabel = remember(msg.timestamp) { formatDatePill(msg.timestamp) }
                    val prevDateLabel = remember(index, messages.size) {
                        if (index > 0) formatDatePill(messages[index - 1].timestamp) else ""
                    }
                    if (index == 0 || currentDateLabel != prevDateLabel) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Surface(color = p.datePill, shape = RoundedCornerShape(10.dp)) {
                                Text(currentDateLabel, color = p.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    }

                    val isMine = msg.senderId == myUserId
                    val isSelected = selectedIds.contains(msg.id)
                    val time = remember(msg.timestamp) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp)) }
                    val imgBitmap = remember(msg.mediaBase64, msg.mediaType) {
                        if (msg.mediaType == "IMAGE") MediaHelper.decodeBase64ToImage(msg.mediaBase64) else null
                    }
                    val senderProfile = memberProfiles[msg.senderId]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) p.accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - msg.id else selectedIds + msg.id
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (isSelected) selectedIds - msg.id else selectedIds + msg.id
                                }
                            )
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (chat.isGroup && !isMine) {
                            AvatarImage(
                                avatarUrl = getAvatarUrl(msg.senderId, senderProfile?.avatarVersion ?: 0L),
                                avatarBase64 = senderProfile?.avatarBase64,
                                size = 28.dp,
                                modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                            )
                        }

                        Surface(
                            color = if (isMine) p.outBubble else p.inBubble,
                            shape = RoundedCornerShape(
                                topStart = if (isMine) 14.dp else 4.dp,
                                topEnd = if (isMine) 4.dp else 14.dp,
                                bottomStart = 14.dp,
                                bottomEnd = 14.dp
                            ),
                            shadowElevation = if (p.isDark) 0.dp else 1.dp,
                            modifier = Modifier.widthIn(min = 90.dp, max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                if (chat.isGroup && !isMine) {
                                    val senderLabel = senderProfile?.displayName?.ifBlank { msg.senderId } ?: msg.senderId
                                    Text(senderLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.accent)
                                }
                                if (msg.replyToText != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 5.dp)
                                            .background(Color.Black.copy(alpha = if (p.isDark) 0.22f else 0.06f), RoundedCornerShape(8.dp))
                                            .padding(7.dp)
                                    ) {
                                        Box(Modifier.width(3.dp).height(28.dp).background(p.accent))
                                        Spacer(Modifier.width(8.dp))
                                        Text(msg.replyToText, fontSize = 12.5.sp, color = p.textSecondary, maxLines = 2)
                                    }
                                }
                                when {
                                    msg.isDeleted -> Text(msg.content, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = p.textSecondary)
                                    msg.mediaType == "IMAGE" && imgBitmap != null -> {
                                        Image(
                                            bitmap = imgBitmap,
                                            contentDescription = "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (isSelectionMode) {
                                                        selectedIds = if (isSelected) selectedIds - msg.id else selectedIds + msg.id
                                                    } else {
                                                        viewingPhotoMsg = msg
                                                    }
                                                }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    msg.mediaType == "VOICE" && msg.mediaBase64 != null -> {
                                        val isPlaying = playingMsgId == msg.id
                                        val bars = remember(msg.id) {
                                            val seed = abs(msg.id.hashCode())
                                            List(22) { idx -> 6 + ((seed + idx * 17) % 18) }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(240.dp).padding(vertical = 4.dp)) {
                                            Box(modifier = Modifier.size(44.dp)) {
                                                AvatarImage(
                                                    avatarUrl = if (isMine) getAvatarUrl(myUserId, myAvatarVersion) else getAvatarUrl(msg.senderId, senderProfile?.avatarVersion ?: chat.avatarVersion),
                                                    avatarBase64 = if (isMine) myAvatarBase64 else (senderProfile?.avatarBase64 ?: chat.avatarBase64),
                                                    size = 42.dp
                                                )
                                                Icon(Icons.Default.Mic, null, tint = p.tickBlue, modifier = Modifier.size(16.dp).align(Alignment.BottomEnd))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    if (isPlaying) {
                                                        MediaHelper.stopPlayback()
                                                        playingMsgId = null
                                                    } else {
                                                        playingMsgId = msg.id
                                                        MediaHelper.playVoiceBase64(context, msg.mediaBase64) { playingMsgId = null }
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = p.textSecondary, modifier = Modifier.size(30.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(26.dp)) {
                                                    Box(Modifier.size(9.dp).clip(CircleShape).background(p.tickBlue))
                                                    bars.forEach { h ->
                                                        Box(Modifier.width(2.5.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(if (isPlaying) p.tickBlue else p.textSecondary.copy(alpha = 0.7f)))
                                                    }
                                                }
                                                Text("0:${msg.durationSec.toString().padStart(2, '0')}", fontSize = 11.sp, color = p.textSecondary)
                                            }
                                        }
                                    }
                                    else -> Text(msg.content, fontSize = 15.5.sp, color = p.textPrimary)
                                }

                                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    if (msg.reaction != null) {
                                        Text(msg.reaction, fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(time, fontSize = 11.sp, color = p.tickGray)
                                    if (isMine && !msg.isDeleted) {
                                        Spacer(Modifier.width(4.dp))
                                        when (msg.status) {
                                            MessageStatus.SENDING -> Icon(Icons.Default.Schedule, null, tint = p.tickGray, modifier = Modifier.size(13.dp))
                                            MessageStatus.SENT -> Icon(Icons.Default.Done, null, tint = p.tickGray, modifier = Modifier.size(15.dp))
                                            MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, tint = p.tickGray, modifier = Modifier.size(15.dp))
                                            MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, tint = p.tickBlue, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // WhatsApp Multi-Select Delete Confirmation Dialog
    if (showDeleteDialog && selectedMessages.isNotEmpty()) {
        val allowEveryone = canDeleteForEveryone(selectedMessages)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = p.surface,
            title = {
                Text(
                    "Delete ${selectedMessages.size} message${if (selectedMessages.size > 1) "s" else ""}?",
                    color = p.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (allowEveryone) {
                        TextButton(
                            onClick = {
                                val ids = selectedMessages.map { it.id }
                                showDeleteDialog = false
                                selectedIds = emptySet()
                                onDeleteForEveryone(ids)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for everyone", color = p.danger, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                        }
                    }
                    TextButton(
                        onClick = {
                            val ids = selectedMessages.map { it.id }
                            showDeleteDialog = false
                            selectedIds = emptySet()
                            onDeleteForMe(ids)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for me", color = p.accent, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                    TextButton(
                        onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = p.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = p.surface,
            title = { Text("Clear this chat?", color = p.textPrimary) },
            text = { Text("Messages will be cleared from your copy of this conversation only.", color = p.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; onClearChat() }) { Text("Clear chat", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = p.accent) }
            }
        )
    }

    if (showDeleteChatConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteChatConfirm = false },
            containerColor = p.surface,
            title = { Text("Delete this chat?", color = p.textPrimary) },
            text = { Text("This deletes the chat from your device only. Other participants still keep their messages.", color = p.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showDeleteChatConfirm = false; onDeleteChat() }) { Text("Delete chat", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatConfirm = false }) { Text("Cancel", color = p.accent) }
            }
        )
    }

    showReactionDialogFor?.let { target ->
        AlertDialog(
            onDismissRequest = { showReactionDialogFor = null },
            containerColor = p.surface,
            title = { Text("React to message", color = p.textPrimary) },
            text = {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    listOf("❤️", "😂", "👍", "🙏", "🔥").forEach { emoji ->
                        TextButton(onClick = { onReact(target.id, emoji); showReactionDialogFor = null }) { Text(emoji, fontSize = 22.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReactionDialogFor = null }) { Text("Close", color = p.accent) } }
        )
    }

    viewingPhotoMsg?.let { photoMsg ->
        val fullBmp = remember(photoMsg.mediaBase64) { MediaHelper.decodeBase64ToImage(photoMsg.mediaBase64) }
        val time = remember(photoMsg.timestamp) { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(photoMsg.timestamp)) }
        Dialog(onDismissRequest = { viewingPhotoMsg = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (fullBmp != null) {
                    Image(fullBmp, "Full Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewingPhotoMsg = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                    }
                    Column {
                        Text(if (photoMsg.senderId == myUserId) "You" else chat.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(time, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatDatePill(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return if (msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    ) {
        "Today"
    } else {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

