package com.globalchat.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
        val dataType = message.data["type"]
        if (dataType == "CALL_OFFER") {
            val callerName = message.data["callerName"] ?: message.data["callerId"] ?: "Someone"
            val isVideo = message.data["callVideo"] == "true"
            showIncomingCallNotification(
                title = callerName,
                body = if (isVideo) "Incoming GlobalChat video call..." else "Incoming GlobalChat voice call..."
            )
            return
        }

        val title = message.notification?.title ?: message.data["title"] ?: "GlobalChat"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        showMessageNotification(title, body)
    }

    private fun showIncomingCallNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "globalchat_calls"
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channel = NotificationChannel(channelId, "GlobalChat Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                ringtoneUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 901, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()

        nm.notify(9001, notification)
    }

    private fun showMessageNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        }

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "globalchat_messages"
        val channel = NotificationChannel(channelId, "GlobalChat Messages", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

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