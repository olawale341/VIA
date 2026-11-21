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
```

---

### File 2 of 2: `android-app/app/src/main/java/com/globalchat/android/ui/ChatScreen.kt`
```kotlin
package com.globalchat.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
    val p = LocalWaPalette.current
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var selectedMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var viewingPhotoMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var showEmojiRow by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val partnerAvatar = remember(chat.avatarBase64) { MediaHelper.decodeBase64ToImage(chat.avatarBase64) }

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
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(p.surfaceVariant), Alignment.Center) {
                            if (partnerAvatar != null) {
                                Image(partnerAvatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person, null, tint = p.textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(chat.title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                            val sub = when {
                                isPartnerTyping -> "typing..."
                                chat.isGroup -> "${chat.memberIdsCsv.split(",").size} participants"
                                isPartnerOnline -> "online"
                                else -> "tap here for info"
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.topBar)
            )
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
                            Text(
                                if (reply.senderId == myUserId) "You" else reply.senderId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = p.accent
                            )
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
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
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
                                Text(
                                    currentDateLabel,
                                    color = p.textSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    val isMine = msg.senderId == myUserId
                    val time = remember(msg.timestamp) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp)) }
                    val imgBitmap = remember(msg.mediaBase64, msg.mediaType) {
                        if (msg.mediaType == "IMAGE") MediaHelper.decodeBase64ToImage(msg.mediaBase64) else null
                    }
                    val voiceAvatar = remember(isMine, myAvatarBase64, chat.avatarBase64) {
                        MediaHelper.decodeBase64ToImage(if (isMine) myAvatarBase64 else chat.avatarBase64)
                    }

                    Box(Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            color = if (isMine) p.outBubble else p.inBubble,
                            shape = RoundedCornerShape(
                                topStart = if (isMine) 14.dp else 4.dp,
                                topEnd = if (isMine) 4.dp else 14.dp,
                                bottomStart = 14.dp,
                                bottomEnd = 14.dp
                            ),
                            shadowElevation = if (p.isDark) 0.dp else 1.dp,
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 300.dp)
                                .combinedClickable(onClick = {}, onLongClick = { selectedMsg = msg })
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                if (chat.isGroup && !isMine) {
                                    Text(msg.senderId, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.accent)
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
                                                .clickable { viewingPhotoMsg = msg }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    msg.mediaType == "VOICE" && msg.mediaBase64 != null -> {
                                        val isPlaying = playingMsgId == msg.id
                                        val bars = remember(msg.id) {
                                            val seed = abs(msg.id.hashCode())
                                            List(22) { idx -> 6 + ((seed + idx * 17) % 18) }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.width(245.dp).padding(vertical = 4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(44.dp)) {
                                                Box(
                                                    Modifier.size(42.dp).clip(CircleShape).background(p.surfaceVariant),
                                                    Alignment.Center
                                                ) {
                                                    if (voiceAvatar != null) Image(voiceAvatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    else Icon(Icons.Default.Person, null, tint = p.textPrimary)
                                                }
                                                Icon(
                                                    Icons.Default.Mic,
                                                    null,
                                                    tint = p.tickBlue,
                                                    modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                                                )
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
                                                Icon(
                                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    null,
                                                    tint = p.textSecondary,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Box(Modifier.size(9.dp).clip(CircleShape).background(p.tickBlue))
                                                    bars.forEach { h ->
                                                        Box(
                                                            Modifier
                                                                .width(2.5.dp)
                                                                .height(h.dp)
                                                                .clip(RoundedCornerShape(2.dp))
                                                                .background(if (isPlaying) p.tickBlue else p.textSecondary.copy(alpha = 0.7f))
                                                        )
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

    selectedMsg?.let { target ->
        AlertDialog(
            onDismissRequest = { selectedMsg = null },
            containerColor = p.surface,
            title = { Text("Message Options", color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("❤️", "😂", "👍", "🙏", "🔥").forEach { emoji ->
                            TextButton(onClick = { onReact(target.id, emoji); selectedMsg = null }) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                    TextButton(onClick = { replyingTo = target; selectedMsg = null }) { Text("Reply", color = p.textPrimary) }
                    if (target.senderId == myUserId && !target.isDeleted) {
                        TextButton(onClick = { onDelete(target.id); selectedMsg = null }) { Text("Delete for everyone", color = p.danger) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMsg = null }) { Text("Close", color = p.accent) } }
        )
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
```

---

Reply **"Batch 3 done"** once those 2 files are committed, and I will immediately send **Batch 4 (the final 2 files: `ExtraScreens.kt` with the System / Light / Dark Theme Picker + `MainActivity.kt`)**!
Here is **Batch 4 (the final 2 files: `ExtraScreens.kt` + `MainActivity.kt`)**—adding the **System / Light / Dark** theme switcher in Settings, live dynamic status bar colors, and connecting all screens together!

---

### File 1 of 2: `android-app/app/src/main/java/com/globalchat/android/ui/ExtraScreens.kt`
```kotlin
package com.globalchat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.globalchat.android.model.ActiveCallUiState
import com.globalchat.android.model.CallStatus
import com.globalchat.android.model.UserProfile
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallScreen(
    call: ActiveCallUiState,
    eglContext: EglBase.Context,
    onAttachRenderers: (SurfaceViewRenderer?, SurfaceViewRenderer?) -> Unit,
    onDetachRenderers: () -> Unit,
    onAccept: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var seconds by remember(call.status) { mutableIntStateOf(0) }
    val partnerAvatar = remember(call.partnerAvatarBase64) { MediaHelper.decodeBase64ToImage(call.partnerAvatarBase64) }

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
                Box(Modifier.size(134.dp).clip(CircleShape).background(Color(0xFF202C33)), Alignment.Center) {
                    if (partnerAvatar != null) {
                        Image(partnerAvatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                }
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
fun SettingsScreen(
    profile: UserProfile,
    onSave: (String, String, String, Boolean, String, Uri?) -> Unit,
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) pickedUri = it }
    val avatarBmp = remember(pickedUri, profile.avatarBase64) {
        if (pickedUri != null) MediaHelper.uriToCompressedBase64(context, pickedUri!!, 300)?.let(MediaHelper::decodeBase64ToImage)
        else MediaHelper.decodeBase64ToImage(profile.avatarBase64)
    }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.textPrimary) }
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
                    Modifier.size(72.dp).clip(CircleShape).background(p.surface).clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    Alignment.Center
                ) {
                    if (avatarBmp != null) Image(avatarBmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.AddAPhoto, null, tint = p.textSecondary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = p.textPrimary)
                    Text(profile.phoneNumber, fontSize = 13.sp, color = p.textSecondary)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.textPrimary,
                    unfocusedTextColor = p.textPrimary,
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.surfaceVariant,
                    focusedLabelColor = p.accent,
                    unfocusedLabelColor = p.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.textPrimary,
                    unfocusedTextColor = p.textPrimary,
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.surfaceVariant,
                    focusedLabelColor = p.accent,
                    unfocusedLabelColor = p.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("Cloud Server Address") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.textPrimary,
                    unfocusedTextColor = p.textPrimary,
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.surfaceVariant,
                    focusedLabelColor = p.accent,
                    unfocusedLabelColor = p.textSecondary
                ),
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
                onClick = { onSave(name, about, serverHost, readReceipts, themeMode, pickedUri); onBack() },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save Changes", color = p.accentText, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
}
```

---

### File 2 of 2: `android-app/app/src/main/java/com/globalchat/android/MainActivity.kt`
```kotlin
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
                        myAvatarBase64 = profile.avatarBase64,
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
                    onRefreshStatuses = vm::refreshStatuses,
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