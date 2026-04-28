package com.fyp.crowdlink.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fyp.crowdlink.R
import com.fyp.crowdlink.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MeshNotificationManager
 *
 * Handles all user-facing alerts for incoming mesh events. Regular messages show a
 * standard high-priority notification. SOS alerts trigger a combination of a full-screen
 * intent, vibration, alarm audio and TTS speech to maximise the chance of the alert
 * being noticed in a noisy environment.
 */
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var sosRingtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        createNotificationChannels()

        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.9f) // slightly slower than default, clearer in noisy environments
            } else {
                Timber.tag("MeshNotificationManager").w("TextToSpeech initialisation failed")
            }
        }
    }

    private fun createNotificationChannels() {
        // Regular incoming message notifications
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming messages from friends via mesh"
        }
        notificationManager.createNotificationChannel(channel)

        // SOS channel - requests DND bypass, user can still override in system settings
        val sosChannel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency alerts from paired friends"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        notificationManager.createNotificationChannel(sosChannel)
    }

    /**
     * Posts a standard notification for an incoming mesh message.
     * Tapping it navigates directly to the chat thread with that friend.
     */
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
            putExtra("navigate_to_chat", friendId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, friendId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(content.take(80)) // truncate preview
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(friendId.hashCode(), notification)
    }

    /**
     * Posts a full-screen SOS notification and triggers vibration, alarm audio and TTS.
     * Vibration and audio are fired independently of the notification system so they
     * still trigger even if the notification is suppressed by the device manufacturer.
     */
    @SuppressLint("FullScreenIntentPolicy")
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

        triggerSosVibration()
        triggerSosAlarm()
        speakSosAlert(senderName, latitude, longitude)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!notificationManager.canUseFullScreenIntent()) {
                Timber.tag("MeshNotificationManager")
                    .w("USE_FULL_SCREEN_INTENT not granted - SOS will not show as full-screen on this device")
            }
        }

        val fullScreenIntent = buildSosFullScreenIntent(
            friendId = friendId,
            senderName = senderName,
            latitude = latitude,
            longitude = longitude
        )

        val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS from $senderName")
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
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
    }

    private fun triggerSosAlarm() {
        try {
            // Store original alarm volume so it can be restored after the alert plays
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            sosRingtone = RingtoneManager.getRingtone(context, alarmUri)
            sosRingtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            sosRingtone?.play()

            // Stop after 4 seconds and restore volume
            Handler(Looper.getMainLooper()).postDelayed({
                sosRingtone?.stop()
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            }, 4000)

        } catch (e: Exception) {
            Timber.tag("MeshNotificationManager").e(e, "Failed to play SOS alarm")
        }
    }

    private fun speakSosAlert(senderName: String, latitude: Double?, longitude: Double?) {
        if (!ttsReady || tts == null) {
            Timber.tag("MeshNotificationManager").w("TTS not ready - skipping speech")
            return
        }

        val message = "Emergency alert from $senderName. $senderName needs help. Tap the notification to navigate to them."

        // Delayed by 3 seconds so the alarm plays first
        Handler(Looper.getMainLooper()).postDelayed({
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH, // interrupt anything currently being spoken
                null,
                "SOS_ALERT"
            )
        }, 3000)
    }

    /**
     * Builds the full-screen PendingIntent that launches the SOS alert screen in MainActivity.
     * Location coordinates are passed as extras so the UI can show a map and distance immediately.
     */
    private fun buildSosFullScreenIntent(
        friendId: String,
        senderName: String,
        latitude: Double?,
        longitude: Double?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("sos_alert_friend_id", friendId)
            putExtra("sos_alert_sender_name", senderName)
            putExtra("sos_alert_received_at", System.currentTimeMillis())
            latitude?.let { putExtra("sos_alert_latitude", it) }
            longitude?.let { putExtra("sos_alert_longitude", it) }
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