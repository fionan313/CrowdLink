package com.fyp.crowdlink.presentation.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * QRScannerScreen
 *
 * This composable is responsible for launching the QR code scanner.
 * It uses the ZXing library (via journeyapps) to scan QR codes and returns the result via a callback.
 */
@Composable
fun QRScannerScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onScanned: (String) -> Unit
) {
    // Launcher for the QR scanning activity
    val launcher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        result.contents?.let { scannedData ->
            // When a QR code is successfully scanned, the data is passed to the onScanned callback
            onScanned(scannedData)
        }
    }
    
    // Automatically launch the scanner when the screen is first composed
    LaunchedEffect(Unit) {
        val options = ScanOptions()
        options.setPrompt("Scan a QR Code") // Message displayed to the user
        options.setBeepEnabled(false)     // Disable beep sound on scan
        options.setOrientationLocked(true) // Lock orientation to match manifest
        launcher.launch(options)
    }
}
