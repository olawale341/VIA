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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        if (call.isVideo) {
            onAttachRenderers(localView, remoteView)
        }
        onDispose {
            if (call.isVideo) onDetachRenderers()
        }
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
                Text("GlobalChat Live Call", fontSize = 12.sp, color = Color(0xFF8696A0))
                Spacer(Modifier.height(18.dp))
                Text(call.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(statusText, fontSize = 15.sp, color = Color(0xFFD9FDD3))
            }

            if (!call.isVideo || call.status != CallStatus.CONNECTED) {
                Box(Modifier.size(130.dp).clip(CircleShape).background(Color(0xFF202C33)), Alignment.Center) {
                    Icon(
                        if (call.isVideo) Icons.Default.Videocam else Icons.Default.Person,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(130.dp))
            }

            if (call.status == CallStatus.INCOMING_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier.size(66.dp).background(Color(0xFFE53935), CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Decline", color = Color.White, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier.size(66.dp).background(Color(0xFF1DAB61), CircleShape)
                        ) {
                            Icon(
                                if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
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
            OutlinedTextField(value = serverHost, onValueChange = { serverHost = it }, label = { Text("Cloud Server Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())

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

            OutlinedButton(onClick = onLogOut, modifier = Modifier.fillMaxWidth()) {
                Text("Log Out / Switch Number", color = Color.Red)
            }
        }
    }
}