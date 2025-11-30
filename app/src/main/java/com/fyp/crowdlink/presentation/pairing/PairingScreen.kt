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

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit,
    onScanClick: () -> Unit
) {
    // Explicitly observing StateFlows
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    
    // Trigger QR generation when screen opens
    LaunchedEffect(Unit) {
        viewModel.generateQRCode()
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair with Friend", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Show QR Code
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
        
        Button(onClick = onScanClick) {
            Text("Scan Friend's QR Code")
        }
        
        // Handle pairing state
        when (pairingState) {
            is PairingState.Success -> {
                LaunchedEffect(Unit) {
                    onPairingSuccess()
                }
            }
            is PairingState.Error -> {
                Text(
                    text = "Error: ${(pairingState as PairingState.Error).message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}