package com.globalchat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.globalchat.android.model.ActiveCallUiState
import com.globalchat.android.model.UserProfile
import kotlinx.coroutines.delay

@Composable
fun ActiveCallScreen(call: ActiveCallUiState, onEndCall: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2500)
        while (true) {
            seconds++
            delay(1000)
        }
    }

    val statusText = if (seconds == 0) {
        if (call.isVideo) "Video calling..." else "Calling..."
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B141A)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text("End-to-end encrypted", fontSize = 12.sp, color = Color(0xFF8696A0))
            Spacer(Modifier.height(20.dp))
            Text(call.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(statusText, fontSize = 15.sp, color = Color(0xFF8696A0))
        }

        Box(Modifier.size(130.dp).clip(CircleShape).background(Color(0xFF202C33)), Alignment.Center) {
            Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { isSpeaker = !isSpeaker },
                modifier = Modifier.size(54.dp).background(if (isSpeaker) Color.White else Color(0xFF202C33), CircleShape)
            ) {
                Icon(Icons.Default.VolumeUp, "Speaker", tint = if (isSpeaker) Color.Black else Color.White)
            }
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier.size(54.dp).background(if (isMuted) Color.White else Color(0xFF202C33), CircleShape)
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", tint = if (isMuted) Color.Black else Color.White)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    onSave: (String, String, String, Boolean, Uri?) -> Unit,
    onBack: () -> Unit,
    onLogOut: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(profile.displayName) }
    var about by remember { mutableStateOf(profile.about) }
    var serverHost by remember { mutableStateOf(profile.serverHost) }
    var readReceipts by remember { mutableStateOf(profile.readReceipts) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) pickedUri = it }
    val avatarBmp = remember(pickedUri, profile.avatarBase64) {
        if (pickedUri != null) MediaHelper.uriToCompressedBase64(context, pickedUri!!, 300)?.let(MediaHelper::decodeBase64ToImage)
        else MediaHelper.decodeBase64ToImage(profile.avatarBase64)
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(70.dp).clip(CircleShape).background(Color(0xFFDFE5E7)).clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    Alignment.Center
                ) {
                    if (avatarBmp != null) Image(avatarBmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(profile.phoneNumber, fontSize = 13.sp, color = Color(0xFF667781))
                }
            }

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = about, onValueChange = { about = it }, label = { Text("About") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = serverHost, onValueChange = { serverHost = it }, label = { Text("Cloud Server Address (Upgrade 2)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Read receipts", fontWeight = FontWeight.Medium)
                    Text("Send and receive blue ticks", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = readReceipts, onCheckedChange = { readReceipts = it })
            }

            Button(
                onClick = { onSave(name, about, serverHost, readReceipts, pickedUri); onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008069)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onLogOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Out / Switch Number", color = Color.Red)
            }
        }
    }
}