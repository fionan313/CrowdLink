package com.fyp.crowdlink.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus

/**
 * ChatScreen
 *
 * Point-to-point messaging screen for a single friend conversation. Observes the
 * message stream from [MessageViewModel] and displays a live discovery status in the
 * top bar so the user can see whether the friend is currently reachable over the mesh.
 * Discovery is started on entry and stopped when the screen is disposed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    friendId: String,
    friendName: String,
    onNavigateBack: () -> Unit,
    viewModel: MessageViewModel = hiltViewModel()
) {
    val messages by viewModel.getMessages(friendId).collectAsState()
    val discoveryStatus by viewModel.discoveryStatus.collectAsState()
    val isMeshActive by viewModel.isMeshActive.collectAsState()
    var textState by remember { mutableStateOf("") }

    // start discovery on entry, stop on exit to avoid scanning after leaving the screen
    DisposableEffect(friendId) {
        viewModel.onResume(friendId)
        viewModel.discover()
        onDispose {
            viewModel.onPause()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(friendName)
                        // status line goes green when the mesh is active, red when not
                        Text(
                            text = discoveryStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMeshActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.discover() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Discovery")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ChatEmptyState(friendName = friendName)
                    }
                } else {
                    // reverseLayout puts the newest message at the bottom without manual scrolling
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages.reversed()) { message ->
                            MessageBubble(message)
                        }
                    }
                }

                // input row pinned to the bottom, rises with the keyboard via imePadding
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = textState,
                            onValueChange = { textState = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (textState.isNotBlank()) {
                                    viewModel.sendText(textState, friendId)
                                    textState = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

/**
 * ChatEmptyState
 *
 * Shown when no messages exist for this conversation yet.
 * Reminds the user that delivery is over BLE mesh with no internet required.
 */
@Composable
fun ChatEmptyState(friendName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Send a message to $friendName below. Messages are routed over the BLE mesh — no internet required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * MessageBubble
 *
 * Renders a single message, right-aligned for sent messages and left-aligned for received.
 * Shows the transport type and hop count below the content, and a [StatusIcon] for
 * outbound messages to indicate delivery state.
 */
@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val containerColor = if (message.isSentByMe)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // transport type and hop count give the user mesh delivery context
                    val infoText = buildString {
                        append(message.transportType)
                        if (message.hopCount > 0) {
                            append(" • ${message.hopCount} hops")
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    if (message.isSentByMe) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusIcon(message.deliveryStatus)
                    }
                }
            }
        }
    }
}

/**
 * StatusIcon
 *
 * Maps [MessageStatus] to an icon and colour. Failed messages are shown in error red,
 * delivered messages in primary, all others in the default surface variant colour.
 */
@Composable
fun StatusIcon(status: MessageStatus) {
    val icon = when (status) {
        MessageStatus.PENDING -> Icons.Default.Info
        MessageStatus.SENT -> Icons.Default.Check
        MessageStatus.DELIVERED -> Icons.Default.CheckCircle
        MessageStatus.FAILED -> Icons.Default.Warning
    }

    val color = when (status) {
        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        MessageStatus.DELIVERED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(12.dp),
        tint = color
    )
}