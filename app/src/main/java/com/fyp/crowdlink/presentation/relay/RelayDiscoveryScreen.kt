package com.fyp.crowdlink.presentation.relay

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.RelayNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayDiscoveryScreen(
    onNavigateBack: () -> Unit,
    viewModel: RelayDiscoveryViewModel = hiltViewModel()
) {
    val relays by viewModel.discoveredRelays.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val autoConnect by viewModel.autoConnect.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startScanning()
        onDispose {
            viewModel.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relay Nodes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Auto-connect toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Auto-connect", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect to strongest relay automatically",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { viewModel.setAutoConnect(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connected to Relay", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.disconnectFromRelay() }) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Discovered Relays", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(relays) { relay ->
                    RelayItem(
                        relay = relay,
                        onClick = { viewModel.connectToRelay(relay.deviceId) }
                    )
                }
            }
        }
    }
}

@Composable
fun RelayItem(relay: RelayNode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Build, contentDescription = null) // Router is missing too
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(relay.name, fontWeight = FontWeight.Bold)
                Text(relay.deviceId, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("${relay.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
