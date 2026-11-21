package com.globalchat.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.globalchat.android.data.local.CallLogEntity
import com.globalchat.android.data.local.ChatEntity
import com.globalchat.android.data.local.StatusEntity
import com.globalchat.android.model.PhoneContact
import com.globalchat.android.model.UserProfile
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: UserProfile,
    isConnected: Boolean,
    chats: List<ChatEntity>,
    onlineUsers: Set<String>,
    phoneContacts: List<PhoneContact>,
    statuses: List<StatusEntity>,
    callLogs: List<CallLogEntity>,
    onRequestLoadContacts: () -> Unit,
    onChatClick: (String) -> Unit,
    onStartNewChat: (String, String?) -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onPostStatus: (String, Long, Uri?) -> Unit,
    onStatusViewed: (String) -> Unit,
    onStartCall: (String, String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onLogOut: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showManualNumberDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showStatusComposer by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusEntity?>(null) }

    val contactPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRequestLoadContacts()
        showContactPicker = true
    }

    fun openContactsSheet() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onRequestLoadContacts()
            showContactPicker = true
        } else {
            contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        Column {
                            Text("GlobalChat", color = Color(0xFF1DAB61), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(
                                text = if (isConnected) "Connected" else "Offline • Tap ⋮ Settings for server",
                                fontSize = 11.sp,
                                color = if (isConnected) Color(0xFF1DAB61) else Color(0xFF667781)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    IconButton(onClick = { showStatusComposer = true }) {
                        Icon(Icons.Default.PhotoCamera, "Camera", tint = Color(0xFF111B21))
                    }
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, "Search", tint = Color(0xFF111B21))
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = Color(0xFF111B21))
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(text = { Text("New group") }, onClick = {
                                showOverflowMenu = false; showGroupDialog = true
                            })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                showOverflowMenu = false; onOpenSettings()
                            })
                            DropdownMenuItem(text = { Text("Log out", color = Color.Red) }, onClick = {
                                showOverflowMenu = false; onLogOut()
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF005C4B),
                    indicatorColor = Color(0xFFD8FDD2),
                    selectedTextColor = Color(0xFF111B21)
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, "Chats") },
                    label = { Text("Chats") },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DonutLarge, "Updates") },
                    label = { Text("Updates") },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Groups, "Communities") },
                    label = { Text("Communities") },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Call, "Calls") },
                    label = { Text("Calls") },
                    colors = itemColors
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        1 -> showStatusComposer = true
                        2 -> showGroupDialog = true
                        else -> openContactsSheet()
                    }
                },
                containerColor = Color(0xFF1DAB61),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    when (selectedTab) {
                        1 -> Icons.Default.AddAPhoto
                        2 -> Icons.Default.GroupAdd
                        3 -> Icons.Default.AddIcCall
                        else -> Icons.Default.AddComment
                    },
                    contentDescription = "Action"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalDivider(color = Color(0xFFF0F2F5))
            when (selectedTab) {
                0 -> ChatsTab(
                    chats = chats.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) },
                    onlineUsers = onlineUsers,
                    onChatClick = onChatClick
                )
                1 -> UpdatesTab(
                    myProfile = profile,
                    statuses = statuses,
                    onAddStatus = { showStatusComposer = true },
                    onViewStatus = {
                        onStatusViewed(it.id)
                        viewingStatus = it
                    }
                )
                2 -> CommunitiesTab(onCreateGroup = { showGroupDialog = true })
                3 -> CallsTab(callLogs = callLogs, onCallClick = { onStartCall(it.partnerId, it.title, it.isVideo) })
            }
        }
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text("Select contact", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    ListItem(
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1DAB61)), Alignment.Center) {
                                Icon(Icons.Default.Dialpad, null, tint = Color.White)
                            }
                        },
                        headlineContent = { Text("Enter phone number manually", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.clickable {
                            showContactPicker = false
                            showManualNumberDialog = true
                        }
                    )
                    HorizontalDivider()
                    if (phoneContacts.isEmpty()) {
                        Text("No device contacts loaded. Tap above to enter any phone number.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn {
                            items(phoneContacts) { c ->
                                ListItem(
                                    leadingContent = {
                                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDFE5E7)), Alignment.Center) {
                                            Icon(Icons.Default.Person, null, tint = Color.White)
                                        }
                                    },
                                    headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(c.phoneNumber, fontSize = 12.sp) },
                                    modifier = Modifier.clickable {
                                        showContactPicker = false
                                        onStartNewChat(c.phoneNumber, c.name)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Close") } }
        )
    }

    if (showManualNumberDialog) {
        var phone by remember { mutableStateOf("+234") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualNumberDialog = false },
            title = { Text("New Chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number (with +code)") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact name (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { onStartNewChat(phone, name.ifBlank { null }); showManualNumberDialog = false }) { Text("Chat") }
            },
            dismissButton = { TextButton(onClick = { showManualNumberDialog = false }) { Text("Cancel") } }
        )
    }

    if (showGroupDialog) {
        var gName by remember { mutableStateOf("") }
        var members by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("New Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = gName, onValueChange = { gName = it }, label = { Text("Group subject") }, singleLine = true)
                    OutlinedTextField(value = members, onValueChange = { members = it }, label = { Text("Member phones (comma separated)") })
                }
            },
            confirmButton = {
                Button(onClick = { onCreateGroup(gName, members); showGroupDialog = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text("Cancel") } }
        )
    }

    if (showStatusComposer) {
        var caption by remember { mutableStateOf("") }
        var pickedImg by remember { mutableStateOf<Uri?>(null) }
        val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { pickedImg = it }
        AlertDialog(
            onDismissRequest = { showStatusComposer = false },
            title = { Text("Add Status Update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Type a status...") })
                    OutlinedButton(onClick = { imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (pickedImg == null) "Attach Photo (Optional)" else "Photo selected ✓")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onPostStatus(caption, 0xFF008069, pickedImg)
                    showStatusComposer = false
                }) { Text("Post Status") }
            },
            dismissButton = { TextButton(onClick = { showStatusComposer = false }) { Text("Cancel") } }
        )
    }

    viewingStatus?.let { st ->
        val bmp = remember(st.imageBase64) { MediaHelper.decodeBase64ToImage(st.imageBase64) }
        AlertDialog(
            onDismissRequest = { viewingStatus = null },
            containerColor = Color(st.bgColorHex),
            title = { Text(st.ownerName, color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (bmp != null) {
                        Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 300.dp).fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    if (st.caption.isNotBlank()) {
                        Text(st.caption, color = Color.White, fontSize = 18.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewingStatus = null }) { Text("Close", color = Color.White) } }
        )
    }
}

@Composable
private fun ChatsTab(chats: List<ChatEntity>, onlineUsers: Set<String>, onChatClick: (String) -> Unit) {
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tap the green chat button to message your contacts", color = Color(0xFF667781))
        }
        return
    }
    LazyColumn {
        items(chats, key = { it.chatId }) { chat ->
            val time = remember(chat.lastTimestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastTimestamp)) }
            val isOnline = !chat.isGroup && onlineUsers.contains(chat.chatId)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.chatId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier.size(50.dp).clip(CircleShape).background(if (chat.isGroup) Color(0xFF008069) else Color(0xFFCFD8DC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person, null, tint = Color.White)
                    }
                    if (isOnline) {
                        Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF25D366)).border(2.dp, Color.White, CircleShape).align(Alignment.BottomEnd))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF111B21))
                        Text(time, fontSize = 12.sp, color = if (chat.unreadCount > 0) Color(0xFF1DAB61) else Color(0xFF667781))
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF667781), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (chat.unreadCount > 0) {
                            Box(Modifier.clip(CircleShape).background(Color(0xFF1DAB61)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("${chat.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(myProfile: UserProfile, statuses: List<StatusEntity>, onAddStatus: () -> Unit, onViewStatus: (StatusEntity) -> Unit) {
    val myAvatar = remember(myProfile.avatarBase64) { MediaHelper.decodeBase64ToImage(myProfile.avatarBase64) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Status", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF111B21))
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().clickable { onAddStatus() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFDFE5E7)), Alignment.Center) {
                        if (myAvatar != null) Image(myAvatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.Person, null, tint = Color.White)
                    }
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1DAB61)).border(2.dp, Color.White, CircleShape).align(Alignment.BottomEnd), Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("My status", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Tap to add status update", fontSize = 13.sp, color = Color(0xFF667781))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Recent updates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667781))
        }
        items(statuses, key = { it.id }) { st ->
            val time = remember(st.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.timestamp)) }
            Row(modifier = Modifier.fillMaxWidth().clickable { onViewStatus(st) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(50.dp).clip(CircleShape).border(2.5.dp, if (st.viewed) Color.LightGray else Color(0xFF1DAB61), CircleShape).padding(3.dp).clip(CircleShape).background(Color(st.bgColorHex)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(st.ownerName, fontWeight = FontWeight.SemiBold)
                    Text("$time • ${st.caption.ifBlank { "Photo status" }}", fontSize = 13.sp, color = Color(0xFF667781), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(onCreateGroup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Groups, null, tint = Color(0xFF008069), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(14.dp))
        Text("Stay connected with a community", fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        Text("Communities bring members together in topic-based groups and announcement channels.", color = Color(0xFF667781))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreateGroup, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008069))) {
            Text("Start your community", color = Color.White)
        }
    }
}

@Composable
private fun CallsTab(callLogs: List<CallLogEntity>, onCallClick: (CallLogEntity) -> Unit) {
    if (callLogs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No recent calls. Open any chat and tap Call or Video!", color = Color(0xFF667781))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(callLogs, key = { it.id }) { call ->
            val time = remember(call.timestamp) { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(call.timestamp)) }
            Row(modifier = Modifier.fillMaxWidth().clickable { onCallClick(call) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFDFE5E7)), Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(call.title, fontWeight = FontWeight.SemiBold, color = if (call.isMissed) Color.Red else Color(0xFF111B21))
                    Text(time, fontSize = 13.sp, color = Color(0xFF667781))
                }
                Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color(0xFF008069))
            }
        }
    }
}