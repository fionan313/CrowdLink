package com.fyp.crowdlink.presentation.friends

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.presentation.sos.SosViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * FriendsScreen
 *
 * Primary screen for managing paired friends and sending SOS alerts. Lists all friends
 * stored in Room with their pairing date and last seen time. The SOS button requires a
 * long press followed by a confirmation dialogue to prevent accidental activation.
 * Tapping a friend opens their chat; the compass icon navigates to the find screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FriendsScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToCompass: (String, String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
    sosViewModel: SosViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    var friendToDelete by remember { mutableStateOf<Friend?>(null) }

    val isSending by sosViewModel.isSending.collectAsState()
    val sosSent by sosViewModel.sosSent.collectAsState()
    var showSosConfirmDialog by remember { mutableStateOf(false) }

    // confirmation dialogue shown after the user long-presses the SOS button
    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            title = { Text("Send SOS Alert?") },
            text = {
                Text("This will immediately alert all your paired friends with your last known location. Only use this in a genuine emergency.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSosConfirmDialog = false
                        sosViewModel.sendSos()
                    }
                ) {
                    Text("Send SOS", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                actions = {
                    IconButton(onClick = onNavigateToPairing) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No friends paired yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap the add icon to pair with a friend",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(friends, key = { it.deviceId }) { friend ->
                        FriendListItem(
                            friend = friend,
                            onDelete = { friendToDelete = friend },
                            onClick = { onNavigateToChat(friend.deviceId, friend.displayName) },
                            onFindClick = { onNavigateToCompass(friend.deviceId, friend.displayName) }
                        )
                    }
                }
            }

            // SOS button - long press required to prevent accidental activation
            // colour transitions from error red to secondary once the alert has been sent
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .combinedClickable(
                        onClick = { /* intentionally no-op on single tap */ },
                        onLongClick = {
                            if (!sosSent && !isSending) {
                                showSosConfirmDialog = true
                            }
                        }
                    ),
                shape = MaterialTheme.shapes.medium,
                color = if (sosSent) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.error,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (sosSent) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (sosSent) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            isSending -> "Sending Alert..."
                            sosSent -> "SOS Sent Successfully"
                            else -> "SOS — Hold to Send"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (sosSent) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onError
                    )
                }
            }
        }

        // unpair confirmation - also sends a BLE unpair notification to the remote device
        friendToDelete?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendToDelete = null },
                title = { Text("Unpair Friend?") },
                text = {
                    Text("Are you sure you want to unpair ${friend.displayName}? You'll need to scan their QR code again to reconnect.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.unpairFriend(friend)
                            friendToDelete = null
                        }
                    ) {
                        Text("Unpair", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * FriendListItem
 *
 * Displays a single paired friend's name, optional nickname, pairing date and last seen
 * timestamp. Tapping the card opens the chat screen. The compass icon navigates to the
 * find screen; the delete icon triggers the unpair confirmation dialogue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListItem(
    friend: Friend,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onFindClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium
                )

                // optional nickname shown below the display name if set
                friend.nickname?.let { nickname ->
                    Text(
                        text = "\"$nickname\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Paired: ${dateFormat.format(Date(friend.pairedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // last seen timestamp only shown if the friend has been detected at least once
                if (friend.lastSeen > 0) {
                    Text(
                        text = "Last seen: ${dateFormat.format(Date(friend.lastSeen))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onFindClick) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = "Find friend",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Unpair friend",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}