package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AlignFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM registration token: $token")
        // Save token to SharedPreferences so WebAppInterface can pass it to UI if needed
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check whether user has salat tracker enabled if this message is tagged as salat/islamic
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val category = remoteMessage.data["category"] ?: "general"
        val salatEnabled = prefs.getBoolean("salat_module_active", true)

        if (category.equals("salat", ignoreCase = true) || category.equals("islamic", ignoreCase = true)) {
            if (!salatEnabled) {
                Log.d(TAG, "Suppressed Islamic/Salat push notification because salatModuleActive is disabled")
                return
            }
        }

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Align"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: "You have a new update."

        val channelId = if (category.equals("salat", ignoreCase = true)) {
            CHANNEL_PRAYER
        } else {
            CHANNEL_UPDATES
        }

        sendNotification(title, body, channelId)
    }

    private fun sendNotification(title: String, messageBody: String, channelId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = if (channelId == CHANNEL_PRAYER) "Prayer & Salat Reminders" else "App Updates & Announcements"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Align app"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        private const val TAG = "AlignFCM"
        const val PREFS_NAME = "align_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val CHANNEL_PRAYER = "align_prayer_channel"
        const val CHANNEL_UPDATES = "align_updates_channel"
    }
}
