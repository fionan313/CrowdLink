package com.fyp.crowdlink.presentation.discovery

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.NearbyFriend
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateToFriends: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val nearbyFriends by viewModel.nearbyFriends.collectAsState()
    var isDiscovering by remember { mutableStateOf(false) }
    var isAdvertising by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discovery") },
                actions = {
                    IconButton(onClick = onNavigateToFriends) {
                        Icon(Icons.Default.Person, "Friends")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isDiscovering) {
                            viewModel.stopDiscovery()
                            isDiscovering = false
                        } else {
                            viewModel.startDiscovery()
                            isDiscovering = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isDiscovering) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDiscovering) "Stop Scan" else "Start Scan")
                }

                Button(
                    onClick = {
                        if (isAdvertising) {
                            viewModel.stopAdvertising()
                            isAdvertising = false
                        } else {
                            viewModel.startAdvertising()
                            isAdvertising = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isAdvertising) Icons.Default.Close else Icons.Default.LocationOn, // Replaced LocationDisabled with Close
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isAdvertising) "Hide" else "Be Visible")
                }
            }

            Divider()

            // Nearby friends list
            Text(
                text = "Nearby Friends (${nearbyFriends.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (nearbyFriends.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning, // Core icon
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isDiscovering) "Scanning..." else "No friends nearby",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!isDiscovering) {
                            Text(
                                text = "Tap 'Start Scan' to find friends",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nearbyFriends, key = { it.deviceId }) { friend ->
                        NearbyFriendCard(friend = friend)
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyFriendCard(friend: NearbyFriend) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.1f", friend.estimatedDistance)}m away",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Signal: ${friend.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Distance indicator icon (Using Core Icons only)
            // < 5m: Close (CheckCircle)
            // < 15m: Medium (Info)
            // > 15m: Far (Warning)
            Icon(
                when {
                    friend.estimatedDistance < 5 -> Icons.Default.CheckCircle
                    friend.estimatedDistance < 15 -> Icons.Default.Info
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when {
                    friend.estimatedDistance < 5 -> MaterialTheme.colorScheme.primary
                    friend.estimatedDistance < 15 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )
        }
    }
}