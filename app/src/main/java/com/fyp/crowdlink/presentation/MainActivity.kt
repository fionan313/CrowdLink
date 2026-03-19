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
import com.fyp.crowdlink.presentation.discovery.DiscoveryViewModel
import com.fyp.crowdlink.presentation.onboarding.OnboardingScreen
import com.fyp.crowdlink.ui.theme.CrowdLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val discoveryViewModel: DiscoveryViewModel by viewModels()
    private val navigateToChatFriendId = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startMeshServicesIfEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationDeepLink(intent)

        if (hasPermissions()) {
            startMeshServicesIfEnabled()
        } else {
            requestPermissions()
        }

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
                            onChatNavigated = { navigateToChatFriendId.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationDeepLink(intent)
    }

    private fun handleNotificationDeepLink(intent: Intent) {
        // existing chat deep link handling
        intent.getStringExtra("navigate_to_chat")?.let { friendId ->
            navigateToChatFriendId.value = friendId
        }

        // SOS alert deep link — navigate to friends screen
        // so the user can see who sent the alert and respond
        intent.getStringExtra("sos_alert_friend_id")?.let { friendId ->
            // For now, navigate to the friends tab/chat
            navigateToChatFriendId.value = friendId
        }
    }

    private fun startMeshServicesIfEnabled() {
        val sharedPrefs = getSharedPreferences("crowdlink_prefs", MODE_PRIVATE)
        val autoStart = sharedPrefs.getBoolean("auto_start", true)
        
        if (autoStart) {
            discoveryViewModel.startDiscovery()
            discoveryViewModel.startAdvertising()
        }
    }

    private fun hasPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

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
