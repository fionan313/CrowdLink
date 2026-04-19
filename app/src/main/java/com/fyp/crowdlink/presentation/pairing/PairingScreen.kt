package com.fyp.crowdlink.presentation.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * PairingScreen
 *
 * Handles the two-sided QR pairing flow. Device A displays its QR code containing its
 * device ID, display name and AES-256-GCM shared key. Device B scans it and sends a
 * BLE confirmation back. If an incoming request arrives while this screen is open, an
 * [AlertDialog] is shown so the user can accept or decline. Progress feedback is shown
 * inline as the handshake moves through its states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit,
    onScanClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val incomingRequest by viewModel.incomingPairingRequest.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()

    // generate the QR code containing this device's identity and shared key on entry,
    // but only if we aren't already in the middle of a pairing handshake.
    LaunchedEffect(Unit) {
        if (viewModel.pairingState.value is PairingState.Idle) {
            viewModel.generateQRCode()
        }
    }

    // shown when a remote device sends a pairing request to this device over BLE
    incomingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { viewModel.declinePairingRequest() },
            title = { Text("Pairing Request") },
            text = { Text("${request.senderDisplayName} wants to pair with you") },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptPairingRequest(request) }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declinePairingRequest() }) {
                    Text("Decline")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with Friend") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // QR code shown while the bitmap is generating, spinner shown until it's ready
            if (qrCodeBitmap != null) {
                Image(
                    bitmap = qrCodeBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(256.dp)
                )
            } else {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Friend's QR Code")
            }

            // inline feedback as the pairing handshake progresses through its states
            when (pairingState) {
                is PairingState.Pairing -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for friend...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is PairingState.AwaitingConfirmation -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Waiting for friend to accept...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is PairingState.Success -> {
                    // navigate away as soon as success state is emitted
                    LaunchedEffect(Unit) { onPairingSuccess() }
                }
                is PairingState.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (pairingState as PairingState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {}
            }

            // debug telemetry panel shown at the bottom when enabled in settings
            if (showDebugInfo) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = debugInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}