package com.fyp.crowdlink.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ConnectivityBanner
 *
 * persistent warning banner for radio state alerts.
 * informs users when hardware requirements for mesh networking aren't met.
 */
@Composable
fun ConnectivityBanner(
    isBluetoothEnabled: Boolean,
    isWifiEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // only visible if core transport radios are offline
    if (!isBluetoothEnabled || !isWifiEnabled) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer, // error colour palette for high-visibility alerts
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically // centre content vertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        // dynamic strings based on specific radio unavailability
                        !isBluetoothEnabled && !isWifiEnabled -> "Bluetooth and WiFi are off. CrowdLink needs both to work."
                        !isBluetoothEnabled -> "Bluetooth is off. Turn it on to discover friends."
                        else -> "WiFi is off. Turn it on for high-speed connections."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
