package com.fyp.crowdlink.presentation.pairing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun PairingScreen(
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit,
    onScanClick: () -> Unit // Added callback for scanner navigation
) {
    val deviceId by viewModel.myDeviceId.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pair with Friend", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Show QR Code
        QRCodeImage(data = deviceId)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onScanClick) { // Wired up navigation
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
                Text("Error: ${(pairingState as PairingState.Error).message}")
            }
            else -> {}
        }
    }
}

@Composable
fun QRCodeImage(data: String) {
    // Use ZXing to generate QR bitmap
    val qrBitmap = remember(data) {
        generateQRBitmap(data, 512, 512)
    }
    
    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = Modifier.size(256.dp)
    )
}

fun generateQRBitmap(data: String, width: Int, height: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}