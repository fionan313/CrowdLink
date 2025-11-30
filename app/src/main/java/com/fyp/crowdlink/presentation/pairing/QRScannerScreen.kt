package com.fyp.crowdlink.presentation.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QRScannerScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onScanned: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        result.contents?.let { scannedData ->
            // Show dialog to enter friend name
            // Then call viewModel.onQRScanned(scannedData, friendName)
            onScanned(scannedData)
        }
    }
    
    LaunchedEffect(Unit) {
        val options = ScanOptions()
        options.setPrompt("Scan a QR Code")
        options.setBeepEnabled(false)
        options.setOrientationLocked(false)
        launcher.launch(options)
    }
}