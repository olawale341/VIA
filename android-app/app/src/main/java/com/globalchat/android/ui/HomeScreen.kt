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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onRefreshStatuses: () -> Unit,
    onChatClick: (String) -> Unit,
    onStartNewChat: (String, String?, String?) -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onPostStatus: (String, Long, Uri?) -> Unit,
    onStatusViewed: (String) -> Unit,
    onStartCall: (String, String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onLogOut: () -> Unit
) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showManualNumberDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showStatusComposer by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) onRefreshStatuses()
    }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...", color = p.textSecondary) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = p.textPrimary,
                                unfocusedTextColor = p.textPrimary
                            )
                        )
                    } else {
                        Column {
                            Text(
                                "GlobalChat",
                                color = if (p.isDark) p.textPrimary else p.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(
                                text = if (isConnected) "Connected" else "Connecting...",
                                fontSize = 11.sp,
                                color = if (isConnected) p.accent else p.textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.topBar),
                actions = {
                    IconButton(onClick = { showStatusComposer = true }) {
                        Icon(Icons.Default.PhotoCamera, "Camera", tint = p.textPrimary)
                    }
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, "Search", tint = p.textPrimary)
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = p.textPrimary)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = p.surface
                        ) {
                            DropdownMenuItem(text = { Text("New group", color = p.textPrimary) }, onClick = {
                                showOverflowMenu = false; showGroupDialog = true
                            })
                            DropdownMenuItem(text = { Text("Settings", color = p.textPrimary) }, onClick = {
                                showOverflowMenu = false; onOpenSettings()
                            })
                            DropdownMenuItem(text = { Text("Log out", color = p.danger) }, onClick = {
                                showOverflowMenu = false; showLogoutConfirm = true
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = p.topBar, tonalElevation = 2.dp) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = p.accentPillIcon,
                    unselectedIconColor = p.textPrimary,
                    indicatorColor = p.accentPill,
                    selectedTextColor = p.textPrimary,
                    unselectedTextColor = p.textSecondary
                )
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Chat, "Chats") }, label = { Text("Chats") }, colors = itemColors)
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.DonutLarge, "Updates") }, label = { Text("Updates") }, colors = itemColors)
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Groups, "Communities") }, label = { Text("Communities") }, colors = itemColors)
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Call, "Calls") }, label = { Text("Calls") }, colors = itemColors)
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
                containerColor = p.accent,
                contentColor = p.accentText,
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
            HorizontalDivider(color = p.divider)
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
                    onRefresh = onRefreshStatuses,
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

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = p.surface,
            title = { Text("Log out of GlobalChat?", color = p.textPrimary) },
            text = { Text("You can log straight back in anytime using your same phone number and email.", color = p.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogOut() }) {
                    Text("Log out", color = p.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = p.accent) }
            }
        )
    }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            containerColor = p.surface,
            title = { Text("Select contact", fontWeight = FontWeight.Bold, color = p.textPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 380.dp)) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent), Alignment.Center) {
                                Icon(Icons.Default.Dialpad, null, tint = p.accentText)
                            }
                        },
                        headlineContent = { Text("Enter phone number manually", fontWeight = FontWeight.SemiBold, color = p.textPrimary) },
                        modifier = Modifier.clickable {
                            showContactPicker = false
                            showManualNumberDialog = true
                        }
                    )
                    HorizontalDivider(color = p.divider)
                    if (phoneContacts.isEmpty()) {
                        Text("No device contacts loaded. Tap above to enter any phone number.", fontSize = 13.sp, color = p.textSecondary, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn {
                            items(phoneContacts) { c ->
                                val bmp = remember(c.avatarBase64) { MediaHelper.decodeBase64ToImage(c.avatarBase64) }
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    leadingContent = {
                                        Box(
                                            Modifier.size(42.dp).clip(CircleShape).background(if (c.isOnGlobalChat) p.accent else p.surfaceVariant),
                                            Alignment.Center
                                        ) {
                                            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            else Icon(Icons.Default.Person, null, tint = if (c.isOnGlobalChat) p.accentText else p.textPrimary)
                                        }
                                    },
                                    headlineContent = { Text(c.name, fontWeight = FontWeight.Medium, color = p.textPrimary) },
                                    supportingContent = {
                                        Text(
                                            if (c.isOnGlobalChat) "On GlobalChat ✓ • ${c.about}" else c.phoneNumber,
                                            fontSize = 12.sp,
                                            color = if (c.isOnGlobalChat) p.accent else p.textSecondary,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        showContactPicker = false
                                        onStartNewChat(c.phoneNumber, c.name, c.avatarBase64)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text("Close", color = p.accent) } }
        )
    }

    if (showManualNumberDialog) {
        var phone by remember { mutableStateOf("+234") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualNumberDialog = false },
            containerColor = p.surface,
            title = { Text("New Chat", color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number (with +code)") }, singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact name (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = p.accent), onClick = {
                    onStartNewChat(phone, name.ifBlank { null }, null)
                    showManualNumberDialog = false
                }) { Text("Chat", color = p.accentText) }
            },
            dismissButton = { TextButton(onClick = { showManualNumberDialog = false }) { Text("Cancel", color = p.textSecondary) } }
        )
    }

    if (showGroupDialog) {
        var gName by remember { mutableStateOf("") }
        var members by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            containerColor = p.surface,
            title = { Text("New Group", color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = gName, onValueChange = { gName = it }, label = { Text("Group subject") }, singleLine = true)
                    OutlinedTextField(value = members, onValueChange = { members = it }, label = { Text("Member phones (comma separated)") })
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = p.accent), onClick = {
                    onCreateGroup(gName, members)
                    showGroupDialog = false
                }) { Text("Create", color = p.accentText) }
            },
            dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text("Cancel", color = p.textSecondary) } }
        )
    }

    if (showStatusComposer) {
        var caption by remember { mutableStateOf("") }
        var pickedImg by remember { mutableStateOf<Uri?>(null) }
        val bgPalette = listOf(0xFF005C4B, 0xFF1F2C34, 0xFF6A1B9A, 0xFF1565C0, 0xFFC62828)
        var selectedBg by remember { mutableLongStateOf(bgPalette.first()) }
        val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { pickedImg = it }
        AlertDialog(
            onDismissRequest = { showStatusComposer = false },
            containerColor = Color(selectedBg),
            title = { Text("New Status", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        placeholder = { Text("Type a status...", color = Color.White.copy(alpha = 0.7f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bgPalette.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(if (selectedBg == hex) 2.5.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { selectedBg = hex }
                            )
                        }
                    }
                    OutlinedButton(onClick = { imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.Image, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (pickedImg == null) "Attach Photo (Optional)" else "Photo selected ✓", color = Color.White)
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent),
                    onClick = { onPostStatus(caption, selectedBg, pickedImg); showStatusComposer = false }
                ) { Text("Post", color = p.accentText) }
            },
            dismissButton = { TextButton(onClick = { showStatusComposer = false }) { Text("Cancel", color = Color.White) } }
        )
    }

    viewingStatus?.let { st ->
        val bmp = remember(st.imageBase64) { MediaHelper.decodeBase64ToImage(st.imageBase64) }
        val avatarBmp = remember(st.ownerAvatarBase64) { MediaHelper.decodeBase64ToImage(st.ownerAvatarBase64) }
        val time = remember(st.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.timestamp)) }
        Dialog(onDismissRequest = { viewingStatus = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (bmp != null) Color.Black else Color(st.bgColorHex))
                    .clickable { viewingStatus = null }
            ) {
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 14.dp, end = 14.dp)) {
                    LinearProgressIndicator(
                        progress = { 1f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewingStatus = null }) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1F2C34)), Alignment.Center) {
                            if (avatarBmp != null) Image(avatarBmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.Person, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(st.ownerName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Today, $time", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }

                if (st.caption.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(if (bmp != null) Alignment.BottomCenter else Alignment.Center)
                            .fillMaxWidth()
                            .background(if (bmp != null) Color.Black.copy(alpha = 0.55f) else Color.Transparent)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            st.caption,
                            color = Color.White,
                            fontSize = if (bmp != null) 16.sp else 24.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (bmp != null) FontWeight.Normal else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatsTab(chats: List<ChatEntity>, onlineUsers: Set<String>, onChatClick: (String) -> Unit) {
    val p = LocalWaPalette.current
    if (chats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tap the green chat button to message your contacts", color = p.textSecondary)
        }
        return
    }
    LazyColumn {
        items(chats, key = { it.chatId }) { chat ->
            val time = remember(chat.lastTimestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastTimestamp)) }
            val isOnline = !chat.isGroup && onlineUsers.contains(chat.chatId)
            val avatarBmp = remember(chat.avatarBase64) { MediaHelper.decodeBase64ToImage(chat.avatarBase64) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.chatId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(if (chat.isGroup) Color(0xFF005C4B) else p.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBmp != null) {
                            Image(avatarBmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(
                                if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person,
                                null,
                                tint = if (chat.isGroup) Color.White else p.textSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    if (isOnline) {
                        Box(Modifier.size(13.dp).clip(CircleShape).background(p.accent).border(2.dp, p.bg, CircleShape).align(Alignment.BottomEnd))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(chat.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.textPrimary)
                        Text(time, fontSize = 12.sp, color = if (chat.unreadCount > 0) p.accent else p.textSecondary)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis, color = p.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (chat.unreadCount > 0) {
                            Box(Modifier.clip(CircleShape).background(p.accent).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("${chat.unreadCount}", color = p.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTab(
    myProfile: UserProfile,
    statuses: List<StatusEntity>,
    onAddStatus: () -> Unit,
    onRefresh: () -> Unit,
    onViewStatus: (StatusEntity) -> Unit
) {
    val p = LocalWaPalette.current
    val myAvatar = remember(myProfile.avatarBase64) { MediaHelper.decodeBase64ToImage(myProfile.avatarBase64) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Status", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = p.textPrimary)
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = p.textSecondary) }
            }
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.surface)
                            .clickable { onAddStatus() }
                            .padding(10.dp)
                    ) {
                        Box(Modifier.size(38.dp).align(Alignment.TopStart)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(p.surfaceVariant), Alignment.Center) {
                                if (myAvatar != null) Image(myAvatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Icon(Icons.Default.Person, null, tint = p.textPrimary)
                            }
                            Box(Modifier.size(18.dp).clip(CircleShape).background(p.accent).border(1.5.dp, p.surface, CircleShape).align(Alignment.BottomEnd), Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = p.accentText, modifier = Modifier.size(12.dp))
                            }
                        }
                        Text("Add status", color = p.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }

                items(statuses, key = { it.id }) { st ->
                    val img = remember(st.imageBase64) { MediaHelper.decodeBase64ToImage(st.imageBase64) }
                    val av = remember(st.ownerAvatarBase64) { MediaHelper.decodeBase64ToImage(st.ownerAvatarBase64) }
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(st.bgColorHex))
                            .clickable { onViewStatus(st) }
                    ) {
                        if (img != null) {
                            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        } else if (st.caption.isNotBlank()) {
                            Text(
                                st.caption,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center).padding(8.dp)
                            )
                        }
                        Box(
                            Modifier
                                .padding(8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (st.viewed) p.textSecondary else p.accent, CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(p.surface)
                                .align(Alignment.TopStart),
                            Alignment.Center
                        ) {
                            if (av != null) Image(av, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.Person, null, tint = p.textPrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            st.ownerName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Recent updates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
            Spacer(Modifier.height(8.dp))
        }

        if (statuses.isEmpty()) {
            item { Text("No status updates in the last 24 hours. Tap 'Add status' above!", color = p.textSecondary, fontSize = 13.5.sp) }
        } else {
            items(statuses, key = { "row_${it.id}" }) { st ->
                val time = remember(st.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.timestamp)) }
                val av = remember(st.ownerAvatarBase64) { MediaHelper.decodeBase64ToImage(st.ownerAvatarBase64) }
                Row(modifier = Modifier.fillMaxWidth().clickable { onViewStatus(st) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape).border(2.5.dp, if (st.viewed) p.textSecondary else p.accent, CircleShape).padding(3.dp).clip(CircleShape).background(Color(st.bgColorHex)),
                        Alignment.Center
                    ) {
                        if (av != null) Image(av, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(st.ownerName, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                        Text("Today, $time • ${st.caption.ifBlank { "Photo" }}", fontSize = 13.sp, color = p.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(onCreateGroup: () -> Unit) {
    val p = LocalWaPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Groups, null, tint = p.accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(14.dp))
        Text("Stay connected with a community", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = p.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Communities bring members together in topic-based groups and announcement channels.", color = p.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreateGroup, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = p.accent)) {
            Text("Start your community", color = p.accentText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CallsTab(callLogs: List<CallLogEntity>, onCallClick: (CallLogEntity) -> Unit) {
    val p = LocalWaPalette.current
    if (callLogs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No recent calls. Open any chat and tap Call or Video!", color = p.textSecondary)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(callLogs, key = { it.id }) { call ->
            val time = remember(call.timestamp) { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(call.timestamp)) }
            Row(modifier = Modifier.fillMaxWidth().clickable { onCallClick(call) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(p.surface), Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = p.textSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(call.title, fontWeight = FontWeight.SemiBold, color = if (call.isMissed) p.danger else p.textPrimary)
                    Text(time, fontSize = 13.sp, color = p.textSecondary)
                }
                Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = p.accent)
            }
        }
    }
}