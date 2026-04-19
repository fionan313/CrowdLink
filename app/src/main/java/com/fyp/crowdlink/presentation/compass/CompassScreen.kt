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

/**
 * CompassScreen
 *
 * Displays direction and distance to a paired friend. Operates in two modes:
 * GPS mode when both devices have an active fix, showing an animated bearing arrow
 * and distance in metres; and RSSI fallback mode indoors when no GPS fix is available,
 * showing signal strength bars derived from BLE RSSI. Location is shared to the mesh
 * every 30 seconds while the screen is active.
 */
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
        viewModel.refreshIndoorOverride()
    }

    // broadcast this device's location over the mesh every 30 seconds while tracking
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                    // GPS MODE - animate the arrow toward the friend's bearing
                    var currentRotation by remember { mutableFloatStateOf(0f) }
                    val targetRotation = (bearing!! - heading + 360) % 360

                    LaunchedEffect(targetRotation) {
                        // shortest path rotation prevents the arrow spinning the long way round
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

                    // BLE RSSI distance shown as a secondary confidence indicator alongside GPS
                    rssiDistance?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Signal distance: ~${it.toInt()}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                } else {
                    // RSSI FALLBACK MODE - no GPS fix available, use signal strength only
                    IndoorModeIndicator(rssiDistance)

                    // warn the user if the displayed distance is from a stale GPS fix
                    if (distance != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Last GPS fix: ~${distance!!.toInt()}m (may be outdated)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
 * shortestRotation
 *
 * Returns the shortest angular path from [from] to [to], preventing the arrow from
 * spinning the long way round when crossing the 0/360 degree boundary.
 */
fun shortestRotation(from: Float, to: Float): Float {
    var diff = (to - from + 360) % 360
    if (diff > 180) diff -= 360
    return from + diff
}

/**
 * IndoorModeIndicator
 *
 * Shown when no GPS fix is available. Displays three signal bars filled based on
 * RSSI distance thresholds - each bar represents a 10-metre proximity band.
 */
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

        // three bars, each taller than the last - filled when within that distance band
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

/**
 * LocationPermissionRationale
 *
 * Shown when location permission has not been granted. Explains why the permission
 * is needed and provides a button to re-trigger the system permission prompt.
 */
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