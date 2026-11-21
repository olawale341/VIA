package com.globalchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.model.CallLogItem
import com.globalchat.android.model.StatusUpdateItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    myUserId: String,
    isConnected: Boolean,
    chats: List<ChatEntity>,
    onlineUsers: Set<String>,
    statusUpdates: List<StatusUpdateItem>,
    callLogs: List<CallLogItem>,
    onChatClick: (String) -> Unit,
    onStartNewChat: (String) -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        Column {
                            Text("GlobalChat", fontWeight = FontWeight.Bold)
                            Text(
                                text = "$myUserId • ${if (isConnected) "Connected" else "Offline Mode"}",
                                fontSize = 11.sp,
                                color = if (isConnected) Color(0xFFD9FDD3) else Color(0xFFFFCDD2)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF075E54),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { showGroupDialog = true }) {
                        Icon(Icons.Default.GroupAdd, "New Group")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Switch Account")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, "Chats") },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DonutLarge, "Updates") },
                    label = { Text("Updates") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Groups, "Communities") },
                    label = { Text("Communities") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Call, "Calls") },
                    label = { Text("Calls") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.AddComment, "New Chat")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> ChatsTab(
                    chats = chats.filter {
                        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
                    },
                    onlineUsers = onlineUsers,
                    onChatClick = onChatClick
                )
                1 -> UpdatesTab(statusUpdates)
                2 -> CommunitiesTab(onCreateGroup = { showGroupDialog = true })
                3 -> CallsTab(callLogs)
            }
        }
    }

    if (showNewChatDialog) {
        var targetPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Start New Chat") },
            text = {
                OutlinedTextField(
                    value = targetPhone,
                    onValueChange = { targetPhone = it },
                    label = { Text("Recipient Phone / ID") },
                    placeholder = { Text("+2348022222222") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onStartNewChat(targetPhone)
                    showNewChatDialog = false
                }) { Text("Chat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var membersCsv by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Create Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = membersCsv,
                        onValueChange = { membersCsv = it },
                        label = { Text("Member IDs (comma-separated)") },
                        placeholder = { Text("+2348022222222, +2348033333333") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateGroup(groupName, membersCsv)
                    showGroupDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChatsTab(
    chats: List<ChatEntity>,
    onlineUsers: Set<String>,
    onChatClick: (String) -> Unit
) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tap the green button below to start a chat!", color = Color.Gray)
        }
        return
    }
    LazyColumn {
        items(chats, key = { it.chatId }) { chat ->
            val time = remember(chat.lastTimestamp) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastTimestamp))
            }
            val isOnline = !chat.isGroup && onlineUsers.contains(chat.chatId)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChatClick(chat.chatId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (chat.isGroup) Color(0xFF128C7E) else Color(0xFFCFD8DC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366))
                                .align(Alignment.BottomEnd)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(time, fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            chat.lastMessage,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (chat.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("${chat.unreadCount}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F0F0))
        }
    }
}

@Composable
private fun UpdatesTab(items: List<StatusUpdateItem>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
        }
        items(items) { status ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (status.viewed) Color.LightGray else Color(0xFF25D366)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(status.userName, fontWeight = FontWeight.SemiBold)
                    Text("${status.timeLabel} • ${status.caption}", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(onCreateGroup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Groups, null, tint = Color(0xFF128C7E), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Stay connected with a community", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("Bring members together in topic-based groups and announcement channels.", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreateGroup, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54))) {
            Text("Start a Group / Community")
        }
    }
}

@Composable
private fun CallsTab(logs: List<CallLogItem>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(logs) { call ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFECE5DD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Color(0xFF075E54))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(call.name, fontWeight = FontWeight.SemiBold, color = if (call.isMissed) Color.Red else Color.Black)
                    Text(call.timeLabel, fontSize = 13.sp, color = Color.Gray)
                }
                Icon(
                    if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color(0xFF075E54)
                )
            }
        }
    }
}