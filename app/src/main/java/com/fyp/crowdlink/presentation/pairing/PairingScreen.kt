package com.fyp.crowdlink.presentation.pairing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * PairingScreen
 *
 * This composable screen allows users to pair with friends by either displaying a QR code
 * or scanning a friend's QR code. It interacts with the [PairingViewModel] to manage state.
 */
@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit,
    onScanClick: () -> Unit
) {
    // Explicitly observing StateFlows from the ViewModel
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val incomingRequest by viewModel.incomingPairingRequest.collectAsState()
    
    // Trigger QR generation when the screen is first launched
    LaunchedEffect(Unit) {
        viewModel.generateQRCode()
    }

    // Step 5: Show confirmation dialog
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
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair with Friend", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Show QR Code if generated, otherwise show a loading indicator
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
        
        // Button to initiate QR scanning flow
        Button(onClick = onScanClick) {
            Text("Scan Friend's QR Code")
        }
        
        // Handle pairing state changes (Success, Error, etc.)
        when (pairingState) {
            is PairingState.AwaitingConfirmation -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Waiting for friend to accept...")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is PairingState.Success -> {
                // Navigate away or show success message when pairing is successful
                LaunchedEffect(Unit) {
                    onPairingSuccess()
                }
            }
            is PairingState.Error -> {
                // Display error message if pairing fails
                Text(
                    text = "Error: ${(pairingState as PairingState.Error).message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}
