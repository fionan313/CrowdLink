package com.fyp.crowdlink.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Message

/**
 * ChatScreen
 *
 * This screen allows the user to exchange messages with a paired friend over WiFi Direct.
 * It manages the lifecycle of the [MessageViewModel] to handle background network discovery.
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
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    var textState by remember { mutableStateOf("") }

    // Register WiFi Direct receivers when the screen is visible
    DisposableEffect(Unit) {
        viewModel.onResume()
        viewModel.discover() // Start looking for the friend
        onDispose {
            viewModel.onPause()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(friendName)
                        Text(
                            text = if (connectionInfo?.groupFormed == true) "Connected" else "Connecting...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Message List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true, // Show newest messages at the bottom
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.reversed()) { message ->
                    MessageBubble(message)
                }
            }

            // Connection Logic: If not connected, show a button to find/connect
            if (connectionInfo?.groupFormed != true) {
                Button(
                    onClick = { viewModel.discover() },
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text("Search for $friendName")
                }
            }

            // Input Area
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
                    placeholder = { Text("Type a message...") }
                )
                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            viewModel.sendText(textState, friendId, "me") // Replace "me" with actual ID
                            textState = ""
                        }
                    },
                    enabled = connectionInfo?.groupFormed == true
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val color = if (message.isSentByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = color
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
