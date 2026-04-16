package com.fyp.crowdlink.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fyp.crowdlink.R
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.usecase.ShareLocationUseCase
import com.fyp.crowdlink.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MeshService : Service() {

    @Inject lateinit var shareLocationUseCase: ShareLocationUseCase
    @Inject lateinit var friendRepository: FriendRepository
    @Inject lateinit var sharedPreferences: SharedPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Mesh Network Active")
        startForeground(NOTIFICATION_ID, notification)
        startLocationLoop()
        return START_STICKY
    }

    private fun startLocationLoop() {
        serviceScope.launch {
            while (true) {
                val locationEnabled = sharedPreferences.getBoolean("location_sharing", true)
                if (locationEnabled) {
                    try {
                        val friends = friendRepository.getAllFriends().first()
                        friends.forEach { friend ->
                            shareLocationUseCase(friend.deviceId)
                        }
                        Timber.tag("MeshService").d("Background location broadcast sent")
                    } catch (e: Exception) {
                        Timber.tag("MeshService").e(e, "Background broadcast failed")
                    }
                }
                delay(60_000L)
            }
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrowdLink")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the mesh network is active in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.tag("MeshService").d("Service destroyed")
    }

    companion object {
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
