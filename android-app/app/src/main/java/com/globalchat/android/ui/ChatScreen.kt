package com.globalchat.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.data.local.MessageEntity
import com.globalchat.android.model.MessageStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chat: ChatEntity,
    myUserId: String,
    isPartnerOnline: Boolean,
    isPartnerTyping: Boolean,
    messages: List<MessageEntity>,
    onBack: () -> Unit,
    onSendText: (String, MessageEntity?) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var showEmojiRow by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendImage(uri)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) isRecording = MediaHelper.startVoiceRecording(context)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF111B21))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF008069)), Alignment.Center) {
                            Icon(if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(chat.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111B21))
                            val sub = when {
                                isPartnerTyping -> "typing..."
                                chat.isGroup -> "${chat.memberIdsCsv.split(",").size} participants"
                                isPartnerOnline -> "online"
                                else -> "tap here for contact info"
                            }
                            Text(sub, fontSize = 12.sp, color = if (isPartnerTyping || isPartnerOnline) Color(0xFF1DAB61) else Color(0xFF667781))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onStartCall(true) }) {
                        Icon(Icons.Default.Videocam, "Video Call", tint = Color(0xFF111B21))
                    }
                    IconButton(onClick = { onStartCall(false) }) {
                        Icon(Icons.Default.Call, "Voice Call", tint = Color(0xFF111B21))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFFEFEAE2))) {
                if (showEmojiRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "🎉", "😢").forEach { e ->
                            TextButton(onClick = { inputText += e }) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
                replyingTo?.let { reply ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).height(30.dp).background(Color(0xFF1DAB61)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(reply.senderId, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF008069))
                            Text(reply.content, maxLines = 1, fontSize = 13.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { replyingTo = null }) { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }
                    }
                }

                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.White, RoundedCornerShape(28.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("Recording voice note...", modifier = Modifier.weight(1f), color = Color.Red, fontWeight = FontWeight.Medium)
                        IconButton(onClick = {
                            MediaHelper.stopAndEncodeVoiceNote()
                            isRecording = false
                        }) { Icon(Icons.Default.Delete, "Cancel", tint = Color.Gray) }
                        IconButton(
                            onClick = {
                                val res = MediaHelper.stopAndEncodeVoiceNote()
                                isRecording = false
                                if (res != null) onSendVoice(res.first, res.second)
                            },
                            modifier = Modifier.size(40.dp).background(Color(0xFF1DAB61), CircleShape)
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send Voice", tint = Color.White) }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.weight(1f).background(Color.White, RoundedCornerShape(26.dp)).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showEmojiRow = !showEmojiRow }) {
                                Icon(Icons.Default.EmojiEmotions, "Emoji", tint = Color(0xFF667781))
                            }
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Message", color = Color(0xFF667781)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Default.AttachFile, "Attach Photo", tint = Color(0xFF667781))
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
                            modifier = Modifier.size(48.dp).background(Color(0xFF1DAB61), CircleShape)
                        ) {
                            Icon(
                                if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = "Send or Voice",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color(0xFFEFEAE2)).padding(padding).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == myUserId
                val time = remember(msg.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)) }
                val imgBitmap = remember(msg.mediaBase64, msg.mediaType) {
                    if (msg.mediaType == "IMAGE") MediaHelper.decodeBase64ToImage(msg.mediaBase64) else null
                }

                Box(Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (isMine) Color(0xFFE7FFDB) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 290.dp).combinedClickable(onClick = {}, onLongClick = { selectedMsg = msg })
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            if (chat.isGroup && !isMine) {
                                Text(msg.senderId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF008069))
                            }
                            if (msg.replyToText != null) {
                                Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp)).padding(6.dp)) {
                                    Text(msg.replyToText, fontSize = 12.sp, color = Color.DarkGray, maxLines = 2)
                                }
                            }
                            when {
                                msg.isDeleted -> Text(msg.content, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Color.Gray)
                                msg.mediaType == "IMAGE" && imgBitmap != null -> {
                                    Image(imgBitmap, "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)))
                                }
                                msg.mediaType == "VOICE" && msg.mediaBase64 != null -> {
                                    val isPlaying = playingMsgId == msg.id
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
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
                                            modifier = Modifier.size(36.dp).background(Color(0xFF1DAB61), CircleShape)
                                        ) {
                                            Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("Voice note • ${msg.durationSec}s", fontSize = 13.sp, color = Color(0xFF111B21))
                                    }
                                }
                                else -> Text(msg.content, fontSize = 15.sp, color = Color(0xFF111B21))
                            }
                            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                if (msg.reaction != null) {
                                    Text(msg.reaction, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(time, fontSize = 11.sp, color = Color(0xFF667781))
                                if (isMine && !msg.isDeleted) {
                                    Spacer(Modifier.width(4.dp))
                                    when (msg.status) {
                                        MessageStatus.SENDING -> Icon(Icons.Default.Schedule, null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                                        MessageStatus.SENT -> Icon(Icons.Default.Done, null, tint = Color.Gray, modifier = Modifier.size(15.dp))
                                        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, tint = Color.Gray, modifier = Modifier.size(15.dp))
                                        MessageStatus.READ -> Icon(Icons.Default.DoneAll, null, tint = Color(0xFF34B7F1), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMsg?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedMsg = null },
            title = { Text("Message Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("❤️", "😂", "👍", "🙏", "🔥").forEach { emoji ->
                            TextButton(onClick = { onReact(target.id, emoji); selectedMsg = null }) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                    TextButton(onClick = { replyingTo = target; selectedMsg = null }) { Text("Reply") }
                    if (target.senderId == myUserId && !target.isDeleted) {
                        TextButton(onClick = { onDelete(target.id); selectedMsg = null }) { Text("Delete for everyone", color = Color.Red) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMsg = null }) { Text("Close") } }
        )
    }
}