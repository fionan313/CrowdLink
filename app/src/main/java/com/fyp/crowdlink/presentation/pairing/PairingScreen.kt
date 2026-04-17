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
 * handles secure cryptographic handshake via QR exchange.
 * manages bidirectional pairing state and incoming bond requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit,
    onScanClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    // reactive state bindings for pairing lifecycle
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val incomingRequest by viewModel.incomingPairingRequest.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()
    
    // initialise local identity QR on entry
    LaunchedEffect(Unit) {
        viewModel.generateQRCode()
    }

    // verification dialogue for incoming mesh bond requests
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            
            // display generated QR containing local public key and identity
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
            
            // trigger external scanner flow for peer QR ingestion
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Friend's QR Code")
            }
            
            // qualitative feedback for handshake progress
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
                    LaunchedEffect(Unit) {
                        onPairingSuccess()
                    }
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

            // conditional debug telemetry for mesh diagnostics
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
