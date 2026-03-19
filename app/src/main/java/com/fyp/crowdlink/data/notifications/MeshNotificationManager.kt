package com.fyp.crowdlink.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fyp.crowdlink.R
import com.fyp.crowdlink.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "crowdlink_messages"
        const val CHANNEL_NAME = "CrowdLink Messages"
        const val SOS_NOTIFICATION_ID = 9001
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming messages from friends via mesh"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showMessageNotification(senderName: String, content: String, friendId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to_chat", friendId)  // handle in MainActivity
        }
        val pendingIntent = PendingIntent.getActivity(
            context, friendId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)  // add a notification icon
            .setContentTitle(senderName)
            .setContentText(content.take(80))  // truncate preview
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(friendId.hashCode(), notification)
    }

    fun showSosNotification(
        senderName: String,
        latitude: Double?,
        longitude: Double?,
        friendId: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val locationText = if (latitude != null && longitude != null) {
            "Last known location: ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
        } else {
            "Location unavailable"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 SOS Alert from $senderName")
            .setContentText(locationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$senderName has sent an emergency alert.\n$locationText"
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setFullScreenIntent(buildSosFullScreenIntent(friendId), true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(SOS_NOTIFICATION_ID, notification)
    }

    private fun buildSosFullScreenIntent(friendId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("sos_alert_friend_id", friendId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
