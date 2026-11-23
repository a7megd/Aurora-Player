package com.aurora.player.community

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aurora.player.MainActivity
import com.aurora.player.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WhisperMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        persistToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: "رسالة جديدة"
        val body = message.data["body"] ?: message.notification?.body ?: "لديك إشعار جديد في Whisper"
        val channelId = "whisper_messages"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "رسائل Whisper", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = intent?.let {
            PendingIntent.getActivity(this, 701, it, PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_whisper_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        fun syncToken(context: Context, uid: String) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("fcmTokens").document(token)
                    .set(mapOf("token" to token, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            }
        }

        private fun persistToken(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(mapOf("token" to token, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        }
    }
}
