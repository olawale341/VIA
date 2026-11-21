package com.globalchat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.globalchat.android.ui.ChatScreen
import com.globalchat.android.ui.HomeScreen
import com.globalchat.android.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GlobalChatApp()
            }
        }
    }
}

@Composable
fun GlobalChatApp(vm: MainViewModel = viewModel()) {
    val myUserId by vm.myUserId.collectAsState()
    val activeChatId by vm.activeChatId.collectAsState()
    val chats by vm.chats.collectAsState()
    val messages by vm.activeMessages.collectAsState()
    val isConnected by vm.isConnected.collectAsState()
    val onlineUsers by vm.onlineUsers.collectAsState()
    val typingUsers by vm.typingUsers.collectAsState()

    if (myUserId == null) {
        SignInScreen(onSignIn = vm::signIn)
    } else if (activeChatId != null) {
        val currentChat = chats.find { it.chatId == activeChatId }
        if (currentChat != null) {
            ChatScreen(
                chat = currentChat,
                myUserId = myUserId!!,
                isPartnerOnline = onlineUsers.contains(currentChat.chatId),
                isPartnerTyping = typingUsers.any { currentChat.memberIdsCsv.contains(it) },
                messages = messages,
                onBack = vm::closeChat,
                onSendMessage = vm::sendMessage,
                onTypingChanged = vm::sendTyping,
                onReact = vm::sendReaction,
                onDelete = vm::deleteMessageForEveryone
            )
        }
    } else {
        HomeScreen(
            myUserId = myUserId!!,
            isConnected = isConnected,
            chats = chats,
            onlineUsers = onlineUsers,
            statusUpdates = vm.statusUpdates,
            callLogs = vm.callLogs,
            onChatClick = vm::openChat,
            onStartNewChat = vm::startChat,
            onCreateGroup = vm::createGroup,
            onSignOut = vm::signOut
        )
    }
}

@Composable
fun SignInScreen(onSignIn: (String, String) -> Unit) {
    var phone by remember { mutableStateOf("+2348011111111") }
    var serverHost by remember { mutableStateOf("10.0.2.2:8080") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to GlobalChat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF075E54))
        Spacer(Modifier.height(8.dp))
        Text("Enter your phone number to connect", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = serverHost,
            onValueChange = { serverHost = it },
            label = { Text("Server Address (change when deployed)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSignIn(phone, serverHost) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}