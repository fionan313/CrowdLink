package com.fyp.crowdlink.presentation.chat

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Message

/**
 * ChatScreen
 *
 * This screen allows the user to exchange messages with a paired friend over WiFi Direct.
 * It manages the lifecycle of the [MessageViewModel] to handle background network discovery.
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
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val peers by viewModel.peers.collectAsState()
    var textState by remember { mutableStateOf("") }

    // Register WiFi Direct receivers when the screen is visible
    DisposableEffect(Unit) {
        viewModel.onResume()
        viewModel.discover() // Start looking for the friend
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
                            text = if (connectionInfo?.groupFormed == true) "Connected" else "Not Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionInfo?.groupFormed == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // DEBUG: Button to kill all connections
                    IconButton(
                        onClick = { viewModel.disconnect() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Kill Connections")
                    }
                    IconButton(onClick = { viewModel.discover() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (connectionInfo?.groupFormed == true) {
                // Message List - Only show when connected
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
                        placeholder = { Text("Type a message...") }
                    )
                    IconButton(
                        onClick = {
                            if (textState.isNotBlank()) {
                                viewModel.sendText(textState, friendId)
                                textState = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            } else {
                // Connection Setup UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "WiFi Direct Connection Required",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To chat offline, you must connect to your friend's device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(onClick = { viewModel.discover() }) {
                        Text("Refresh Peer List")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Nearby Devices:", style = MaterialTheme.typography.labelLarge)
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (peers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No devices found yet. Searching...")
                                }
                            }
                        } else {
                            items(peers) { device ->
                                PeerItem(device = device) {
                                    viewModel.connect(device.deviceAddress)
                                }
                            }
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
    val color = if (message.isSentByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = color
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
