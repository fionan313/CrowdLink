package com.fyp.crowdlink.presentation.compass

import android.Manifest
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    friendId: String,
    friendName: String,
    onNavigateBack: () -> Unit,
    viewModel: CompassViewModel = hiltViewModel()
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        permissionState.launchMultiplePermissionRequest()
        viewModel.setFriendId(friendId)
    }

    // Share location every 30 seconds while on this screen
    LaunchedEffect(friendId) {
        while (true) {
            if (permissionState.allPermissionsGranted) {
                viewModel.shareLocation()
            }
            delay(30000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finding $friendName") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (permissionState.allPermissionsGranted) {
                val heading by viewModel.compassHeading.collectAsState()
                val bearing by viewModel.bearingToFriend.collectAsState()
                val distance by viewModel.distanceMetres.collectAsState()
                val isGpsAvailable by viewModel.isGpsAvailable.collectAsState()
                val rssiDistance by viewModel.rssiDistance.collectAsState()

                if (isGpsAvailable && bearing != null) {
                    // Logic for shortest path rotation
                    var currentRotation by remember { mutableStateOf(0f) }
                    val targetRotation = (bearing!! - heading + 360) % 360
                    
                    LaunchedEffect(targetRotation) {
                        currentRotation = shortestRotation(currentRotation, targetRotation)
                    }

                    val animatedRotation by animateFloatAsState(
                        targetValue = currentRotation,
                        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                        label = "ArrowRotation"
                    )

                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Direction Arrow",
                        modifier = Modifier
                            .size(200.dp)
                            .rotate(animatedRotation),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "${distance?.toInt() ?: 0}m",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // Indoor / Low Accuracy Mode
                    IndoorModeIndicator(rssiDistance)
                }
            } else {
                LocationPermissionRationale {
                    permissionState.launchMultiplePermissionRequest()
                }
            }
        }
    }
}

/**
 * Calculates the shortest rotation path to avoid the arrow spinning 350 degrees
 * when jumping from 355 to 5.
 */
fun shortestRotation(from: Float, to: Float): Float {
    var diff = (to - from + 360) % 360
    if (diff > 180) diff -= 360
    return from + diff
}

@Composable
fun IndoorModeIndicator(rssiDistance: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = "GPS Unavailable",
            modifier = Modifier.size(100.dp),
            tint = Color.Gray
        )
        Text("Indoor Mode", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Simple signal bars
        Row(verticalAlignment = Alignment.Bottom) {
            repeat(3) { index ->
                val isFilled = rssiDistance != null && rssiDistance < (index + 1) * 10
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp * (index + 1))
                        .padding(horizontal = 2.dp)
                        .background(if (isFilled) MaterialTheme.colorScheme.primary else Color.LightGray)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (rssiDistance != null) "Est. Distance: ${rssiDistance.toInt()}m" else "Searching...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun LocationPermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Location access is required to find your friends offline.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}
