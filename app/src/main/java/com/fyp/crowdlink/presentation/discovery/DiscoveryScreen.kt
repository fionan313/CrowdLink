package com.fyp.crowdlink.presentation.discovery

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.NearbyFriend

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateToFriends: () -> Unit,
    onNavigateToRelay: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val nearbyFriends by viewModel.nearbyFriends.collectAsState()
    val discoveredRelays by viewModel.discoveredRelays.collectAsState()
    val isRelayConnected by viewModel.isRelayConnected.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()

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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RelayStatusBanner(
                isConnected = isRelayConnected,
                relayCount = discoveredRelays.size,
                onClick = onNavigateToRelay
            )

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isDiscovering) {
                            viewModel.stopDiscovery()
                        } else {
                            viewModel.startDiscovery()
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
                        } else {
                            viewModel.startAdvertising()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isAdvertising) Icons.Default.Close else Icons.Default.LocationOn,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isAdvertising) "Hide" else "Be Visible")
                }
            }

            HorizontalDivider()

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
                            Icons.Default.Warning,
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
fun RelayStatusBanner(isConnected: Boolean, relayCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isConnected) Icons.Default.CheckCircle else Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) "Relay Connected" else "Searching for Relay...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isConnected) {
                    Text(
                        text = "$relayCount relays found",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details")
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
                    text = "%.1fm away".format(friend.estimatedDistance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Signal: ${friend.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
