package com.fyp.crowdlink.presentation.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fyp.crowdlink.domain.model.Friend
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = hiltViewModel(),
    onAddFriend: () -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFriend) {
                Icon(Icons.Default.Add, "Add Friend")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(friends) { friend ->
                FriendListItem(friend = friend)
            }
        }
    }
}

@Composable
fun FriendListItem(friend: Friend) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Paired ${formatTime(friend.pairedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}