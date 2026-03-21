package com.fyp.crowdlink.presentation.discovery

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.NearbyFriend

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateToFriends: () -> Unit,
    onNavigateToCompass: (String, String) -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToMap: (String, String) -> Unit,
    onNavigateToRelay: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val nearbyFriends by viewModel.nearbyFriends.collectAsState()
    val discoveredRelays by viewModel.discoveredRelays.collectAsState()
    val isRelayConnected by viewModel.isRelayConnected.collectAsState()
    val isMeshActive by viewModel.isMeshActive.collectAsState()
    val forceShowRelays by viewModel.forceShowRelays.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby") }
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
            MeshStatusPill(isMeshActive = isMeshActive) {
                if (isMeshActive) {
                    viewModel.stopDiscovery()
                    viewModel.stopAdvertising()
                } else {
                    viewModel.startDiscovery()
                    viewModel.startAdvertising()
                }
            }

            // Only show the relay banner if at least one relay has been found OR debug mode is on
            val relayCount = discoveredRelays.size
            if (relayCount > 0 || forceShowRelays) {
                RelayStatusBanner(
                    isConnected = isRelayConnected,
                    relayCount = relayCount,
                    onClick = onNavigateToRelay
                )
            }

            // Nearby friends list
            Text(
                text = "Nearby Friends (${nearbyFriends.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (nearbyFriends.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isMeshActive) "Scanning for friends…" else "CrowdLink is paused",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isMeshActive)
                            "Friends need to have the app open nearby"
                        else
                            "Tap the status bar above to start scanning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nearbyFriends, key = { it.deviceId }) { friend ->
                        NearbyFriendCard(
                            friend = friend,
                            onFindClick = { onNavigateToCompass(friend.deviceId, friend.displayName) },
                            onMessageClick = { onNavigateToChat(friend.deviceId, friend.displayName) },
                            onMapClick = { onNavigateToMap(friend.deviceId, friend.displayName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeshStatusPill(
    isMeshActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isMeshActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val label = if (isMeshActive) "Active — tap to pause" else "Offline — tap to start"
    val dotColor = if (isMeshActive) Color(0xFF4CAF50) else Color.Gray

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMeshActive) "CrowdLink Active" else "CrowdLink Offline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
fun NearbyFriendCard(
    friend: NearbyFriend,
    onFindClick: () -> Unit,
    onMessageClick: () -> Unit,
    onMapClick: () -> Unit
) {
    val proximityLabel = when {
        friend.estimatedDistance < 5 -> "Very close"
        friend.estimatedDistance < 20 -> "Nearby"
        else -> "In range"
    }

    val proximityColor = when {
        friend.estimatedDistance < 5 -> Color(0xFF4CAF50)   // green
        friend.estimatedDistance < 20 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar initial
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = proximityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = proximityColor
                )
                Text(
                    text = "Signal: ${friend.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            IconButton(onClick = onMapClick) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Map",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onFindClick) {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = "Find",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onMessageClick) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Message",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
