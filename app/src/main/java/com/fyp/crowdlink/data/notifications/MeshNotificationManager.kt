package com.fyp.crowdlink.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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
        const val SOS_CHANNEL_ID = "crowdlink_sos"
        const val SOS_NOTIFICATION_ID = 9001
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Regular messages channel
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming messages from friends via mesh"
            }
            notificationManager.createNotificationChannel(channel)

            // SOS Alert channel — high importance, bypass DND where possible
            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency alerts from paired friends"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)   // requests DND bypass — user can still override in settings
            }
            notificationManager.createNotificationChannel(sosChannel)
        }
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

        // Fire vibration directly — does not rely on notification system
        // so it works even if notification is suppressed by manufacturer
        triggerSosVibration()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!notificationManager.canUseFullScreenIntent()) {
                Log.w("MeshNotificationManager", 
                    "USE_FULL_SCREEN_INTENT not granted — SOS will not show as full-screen on this device")
            }
        }

        val fullScreenIntent = buildSosFullScreenIntent(friendId)

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🆘 SOS from $senderName")
            .setContentText(locationText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$senderName has sent an emergency alert.\n$locationText"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()

        notificationManager.notify(SOS_NOTIFICATION_ID, notification)
    }

    private fun triggerSosVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun buildSosFullScreenIntent(friendId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("sos_alert_friend_id", friendId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            SOS_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
