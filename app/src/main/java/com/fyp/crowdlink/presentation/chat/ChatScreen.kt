package com.fyp.crowdlink.presentation.chat

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus

/**
 * ChatScreen
 *
 * This screen allows the user to exchange messages with a paired friend.
 * It uses the Mesh Network as the primary transport, with background fallbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    friendId: String,
    friendName: String,
    onNavigateBack: () -> Unit,
    viewModel: MessageViewModel = hiltViewModel()
) {
    val messages by viewModel.getMessages(friendId).collectAsState()
    val discoveryStatus by viewModel.discoveryStatus.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val isMeshActive by viewModel.isMeshActive.collectAsState()
    var textState by remember { mutableStateOf("") }
    var showPeerList by remember { mutableStateOf(false) }

    // Lifecycle management for background discovery
    DisposableEffect(Unit) {
        viewModel.onResume()
        viewModel.discover()
        onDispose {
            viewModel.onPause()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(friendName)
                        Text(
                            text = discoveryStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMeshActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPeerList = !showPeerList }) {
                        Icon(
                            if (showPeerList) Icons.Default.Close else Icons.Default.Share,
                            contentDescription = "Connection Options"
                        )
                    }
                    IconButton(onClick = { viewModel.discover() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Message List - Primary UI
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages.reversed()) { message ->
                        MessageBubble(message)
                    }
                }

                // Input Area
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = textState,
                            onValueChange = { textState = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (textState.isNotBlank()) {
                                    viewModel.sendText(textState, friendId)
                                    textState = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }

            // Peer List Overlay (WiFi Direct connections for "High Speed" transport)
            if (showPeerList) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "High-Speed Peer Connection",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Connect directly for faster image and file transfers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = { viewModel.discover() }) {
                            Text("Refresh Peer List")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Nearby Devices:", style = MaterialTheme.typography.labelLarge)

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            if (peers.isEmpty()) {
                                item {
                                    Text(
                                        "No devices found. Ensure WiFi is on.",
                                        modifier = Modifier.padding(32.dp)
                                    )
                                }
                            } else {
                                items(peers) { device ->
                                    PeerItem(device = device) {
                                        viewModel.connect(device.deviceAddress)
                                        showPeerList = false
                                    }
                                }
                            }
                        }
                        
                        Button(
                            onClick = { showPeerList = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Chat")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeerItem(device: WifiP2pDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = device.deviceName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = device.deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = getDeviceStatus(device.status),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun getDeviceStatus(status: Int): String {
    return when (status) {
        WifiP2pDevice.AVAILABLE -> "Available"
        WifiP2pDevice.INVITED -> "Invited"
        WifiP2pDevice.CONNECTED -> "Connected"
        WifiP2pDevice.FAILED -> "Failed"
        WifiP2pDevice.UNAVAILABLE -> "Unavailable"
        else -> "Unknown"
    }
}

@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val containerColor = if (message.isSentByMe) 
        MaterialTheme.colorScheme.primaryContainer 
    else 
        MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Transport and Hop indicator
                    val infoText = buildString {
                        append(message.transportType)
                        if (message.hopCount > 0) {
                            append(" • ${message.hopCount} hops")
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    
                    if (message.isSentByMe) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusIcon(message.deliveryStatus)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: MessageStatus) {
    val icon = when (status) {
        MessageStatus.PENDING -> Icons.Default.Info
        MessageStatus.SENT -> Icons.Default.Check
        MessageStatus.DELIVERED -> Icons.Default.CheckCircle
        MessageStatus.FAILED -> Icons.Default.Warning
    }
    
    val color = when (status) {
        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        MessageStatus.DELIVERED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(12.dp),
        tint = color
    )
}
