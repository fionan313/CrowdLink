package com.fyp.crowdlink.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.fyp.crowdlink.domain.model.SosAlertData
import com.fyp.crowdlink.presentation.discovery.DiscoveryViewModel
import com.fyp.crowdlink.presentation.onboarding.OnboardingScreen
import com.fyp.crowdlink.ui.theme.CrowdLinkTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 *
 * Single-activity entry point for the app. Responsible for:
 * - Requesting runtime permissions on first launch
 * - Auto-starting BLE scanning and advertising if enabled in settings
 * - Routing notification deep links for chat navigation and SOS alerts
 * - Showing the onboarding flow on first launch, then [MainScreen] thereafter
 *
 * Deep link extras written by [MeshNotificationManager] are read in [handleNotificationDeepLink]
 * and passed down to [MainScreen] as state so the correct screen opens automatically.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val discoveryViewModel: DiscoveryViewModel by viewModels()

    // holds the friend ID from a chat notification tap, cleared once navigation completes
    private val navigateToChatFriendId = mutableStateOf<String?>(null)

    // holds parsed SOS alert data from a notification tap, cleared once the alert screen opens
    private val pendingSosAlert = mutableStateOf<SosAlertData?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startMeshServicesIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationDeepLink(intent)

        if (hasPermissions()) startMeshServicesIfEnabled() else requestPermissions()

        setContent {
            CrowdLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sharedPrefs = getSharedPreferences("crowdlink_prefs", MODE_PRIVATE)
                    var onboardingComplete by remember {
                        mutableStateOf(sharedPrefs.getBoolean("onboarding_complete", false))
                    }

                    if (!onboardingComplete) {
                        OnboardingScreen(
                            onComplete = {
                                sharedPrefs.edit { putBoolean("onboarding_complete", true) }
                                onboardingComplete = true
                            }
                        )
                    } else {
                        MainScreen(
                            pendingChatFriendId = navigateToChatFriendId.value,
                            onChatNavigated = { navigateToChatFriendId.value = null },
                            pendingSosAlert = pendingSosAlert.value,
                            onSosAlertNavigated = { pendingSosAlert.value = null }
                        )
                    }
                }
            }
        }
    }

    // called when the activity is already running and a new notification is tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationDeepLink(intent)
    }

    /**
     * Extracts deep link extras from notification intents built by [MeshNotificationManager].
     * Chat notifications carry "navigate_to_chat"; SOS notifications carry "sos_alert_*" extras.
     */
    private fun handleNotificationDeepLink(intent: Intent) {
        intent.getStringExtra("navigate_to_chat")?.let { friendId ->
            navigateToChatFriendId.value = friendId
        }

        intent.getStringExtra("sos_alert_friend_id")?.let { friendId ->
            pendingSosAlert.value = SosAlertData(
                friendId = friendId,
                senderName = intent.getStringExtra("sos_alert_sender_name") ?: "Unknown",
                latitude = if (intent.hasExtra("sos_alert_latitude"))
                    intent.getDoubleExtra("sos_alert_latitude", 0.0) else null,
                longitude = if (intent.hasExtra("sos_alert_longitude"))
                    intent.getDoubleExtra("sos_alert_longitude", 0.0) else null,
                receivedAt = intent.getLongExtra("sos_alert_received_at", System.currentTimeMillis())
            )
        }
    }

    /**
     * Starts BLE discovery and advertising via [DiscoveryViewModel] only if auto-start
     * is enabled in settings. Called after permissions are confirmed.
     */
    private fun startMeshServicesIfEnabled() {
        val sharedPrefs = getSharedPreferences("crowdlink_prefs", MODE_PRIVATE)
        if (sharedPrefs.getBoolean("auto_start", true)) {
            discoveryViewModel.startDiscovery()
            discoveryViewModel.startAdvertising()
        }
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

    /**
     * Returns the set of runtime permissions required for this API level.
     * BLE permission model changed significantly in Android 12 (S) and 13 (Tiramisu).
     */
    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.POST_NOTIFICATIONS
            ))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        return permissions.toTypedArray()
    }
}