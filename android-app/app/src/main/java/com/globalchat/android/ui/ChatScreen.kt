package com.globalchat.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSendMessage: (String, MessageEntity?) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMsgForMenu by remember { mutableStateOf<MessageEntity?>(null) }
    val listState = rememberLazyListState()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                title = {
                    Column {
                        Text(chat.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        val subtitle = when {
                            isPartnerTyping -> "typing..."
                            chat.isGroup -> "${chat.memberIdsCsv.split(",").size} participants"
                            isPartnerOnline -> "online"
                            else -> "offline"
                        }
                        Text(subtitle, fontSize = 12.sp, color = Color(0xFFD9FDD3))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF075E54),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFFECE5DD))) {
                replyingTo?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).height(32.dp).background(Color(0xFF128C7E)))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Replying to ${reply.senderId}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF128C7E))
                            Text(reply.content, maxLines = 1, fontSize = 13.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { replyingTo = null }) {
                            Icon(Icons.Default.Close, "Cancel Reply", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim(), replyingTo)
                                inputText = ""
                                replyingTo = null
                            }
                        },
                        modifier = Modifier.size(48.dp).background(Color(0xFF128C7E), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFECE5DD))
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == myUserId
                val time = remember(msg.timestamp) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (isMine) Color(0xFFD9FDD3) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { selectedMsgForMenu = msg }
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            if (chat.isGroup && !isMine) {
                                Text(msg.senderId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF128C7E))
                            }
                            if (msg.replyToText != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(msg.replyToText, fontSize = 12.sp, color = Color.DarkGray, maxLines = 2)
                                }
                            }
                            Text(
                                text = msg.content,
                                fontSize = 15.sp,
                                fontStyle = if (msg.isDeleted) FontStyle.Italic else FontStyle.Normal,
                                color = if (msg.isDeleted) Color.Gray else Color.Black
                            )
                            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                if (msg.reaction != null) {
                                    Text(msg.reaction, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(time, fontSize = 11.sp, color = Color.Gray)
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

    selectedMsgForMenu?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedMsgForMenu = null },
            title = { Text("Message Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("❤️", "😂", "👍", "🙏", "🔥").forEach { emoji ->
                            TextButton(onClick = {
                                onReact(target.id, emoji)
                                selectedMsgForMenu = null
                            }) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                    TextButton(onClick = {
                        replyingTo = target
                        selectedMsgForMenu = null
                    }) { Text("Reply to message") }
                    if (target.senderId == myUserId && !target.isDeleted) {
                        TextButton(onClick = {
                            onDelete(target.id)
                            selectedMsgForMenu = null
                        }) { Text("Delete for everyone", color = Color.Red) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMsgForMenu = null }) { Text("Close") }
            }
        )
    }
}