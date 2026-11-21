package com.globalchat.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.globalchat.android.MainActivity
import com.globalchat.android.data.local.ProfilePrefs
import com.globalchat.android.data.remote.ChatSocketClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlobalChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val profile = ProfilePrefs(applicationContext).load()
        if (profile.isRegistered && profile.phoneNumber.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                ChatSocketClient().registerFcmToken(profile.serverHost, profile.phoneNumber, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "GlobalChat"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "globalchat_messages"
        val channel = NotificationChannel(channelId, "GlobalChat Messages", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}