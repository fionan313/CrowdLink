package com.fyp.crowdlink.presentation.sos

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SosAlertScreen
 *
 * Full-screen emergency alert UI shown when an SOS is received from a paired friend.
 * Displays a pulsing warning icon on a deep red background, the sender's name and
 * estimated distance, and action buttons to navigate to their location on the map or
 * open their chat. A metadata card shows the alert timestamp, last seen time and GPS
 * coordinates if available. The navigate button is hidden if no coordinates were included
 * in the SOS payload.
 */
@Composable
fun SosAlertScreen(
    friendId: String,
    senderName: String,
    latitude: Double?,
    longitude: Double?,
    receivedAt: Long,
    onNavigateToChat: () -> Unit,
    onNavigateToMap: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: SosViewModel = hiltViewModel()
) {
    LaunchedEffect(friendId) {
        viewModel.loadFriend(friendId)
    }

    val friend by viewModel.friend.collectAsState()
    val myLocation by viewModel.myLocation.collectAsState()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val receivedAtFormatted = remember { timeFormat.format(Date(receivedAt)) }

    // distance calculation requires both the local GPS fix and the sender's coordinates
    val distanceText = remember(myLocation, latitude, longitude) {
        if (myLocation != null && latitude != null && longitude != null) {
            val meters = viewModel.calculateDistance(
                myLocation!!.latitude, myLocation!!.longitude,
                latitude, longitude
            )
            if (meters < 1000) "~${meters.toInt()}m away"
            else "~%.1fkm away".format(meters / 1000.0)
        } else null
    }

    // relative last seen time derived from the friend's Room record
    val lastSeenText = remember(friend) {
        val lastSeen = friend?.lastSeen ?: 0L
        if (lastSeen == 0L) "Never"
        else {
            val diffMs = System.currentTimeMillis() - lastSeen
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                minutes < 1440 -> "${minutes / 60}h ago"
                else -> "${minutes / 1440}d ago"
            }
        }
    }

    // warning icon pulses between full and 30% opacity to draw attention
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C)), // deep red background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // dismiss button anchored to the top-right corner
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // pulsing warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = Color.White.copy(alpha = alpha)
                )

                Text(
                    text = "SOS ALERT",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 4.sp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$senderName needs help",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    // distance only shown when both devices have a GPS fix
                    distanceText?.let {
                        Text(
                            text = it,
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // navigate button only rendered when coordinates were included in the payload
                    if (latitude != null && longitude != null) {
                        Button(
                            onClick = onNavigateToMap,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFB71C1C)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Navigate to ${senderName.split(" ").first()}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToChat,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Message ${senderName.split(" ").first()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // metadata card - alert time, last seen, and coordinates if present
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataRow(label = "Alert received", value = receivedAtFormatted)
                        MetadataRow(label = "Last Seen", value = lastSeenText)
                        if (latitude != null && longitude != null) {
                            MetadataRow(label = "Latitude", value = "%.6f".format(latitude))
                            MetadataRow(label = "Longitude", value = "%.6f".format(longitude))
                        } else {
                            MetadataRow(label = "Location", value = "Not available")
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dismiss",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * MetadataRow
 *
 * Single label-value row used inside the SOS metadata card.
 */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}