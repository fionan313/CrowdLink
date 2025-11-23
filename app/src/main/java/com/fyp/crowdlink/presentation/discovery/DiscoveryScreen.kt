package com.fyp.crowdlink.presentation.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val devices by viewModel.discoveredDevices.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CrowdLink Discovery") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Device Mode Section
            Text(
                text = "Device Mode",
                style = MaterialTheme.typography.titleLarge  // ← Material3 uses titleLarge, not h6
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) { viewModel.startAdvertising() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)  // ← Material3 uses containerColor
                    )
                ) {
                    Text("Become Discoverable")
                }

                Button(
                    onClick = @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) { viewModel.stopAdvertising() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722)  // ← Material3 uses containerColor
                    )
                ) {
                    Text("Stop Advertising")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()  // ← Material3 uses HorizontalDivider, not Divider

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning Section
            Text(
                text = "Nearby Devices (${devices.size})",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceCard(device = device)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN) { viewModel.startDiscovery() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Scan")
                }

                Button(
                    onClick = @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN) { viewModel.stopDiscovery() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error  // ← Material3 uses colorScheme.error
                    )
                ) {
                    Text("Stop Scan")
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: DiscoveredDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Device: ${device.deviceId.takeLast(8)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "RSSI: ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Text(
                text = if (device.estimatedDistance > 0) {
                    "${device.estimatedDistance.roundToInt()}m"
                } else {
                    "Unknown"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}