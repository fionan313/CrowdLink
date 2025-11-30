package com.fyp.crowdlink.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    
    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    
    // Load initial values
    LaunchedEffect(userProfile) {
        userProfile?.let { profile ->
            displayName = profile.displayName
            phoneNumber = profile.phoneNumber ?: ""
            statusMessage = profile.statusMessage ?: ""
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveUserProfile(
                                displayName = displayName,
                                phoneNumber = phoneNumber,
                                statusMessage = statusMessage
                            )
                        },
                        enabled = displayName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Section
            Text(
                text = "Your Profile",
                style = MaterialTheme.typography.titleLarge
            )
            
            Text(
                text = "This information will be shared when pairing with friends",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Required - shown to friends") }
            )
            
            // Phone Number
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = { Text("Optional - for emergency contact") }
            )
            
            // Status Message
            OutlinedTextField(
                value = statusMessage,
                onValueChange = { statusMessage = it },
                label = { Text("Status Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                supportingText = { Text("Optional - e.g., 'At Electric Picnic 2025'") }
            )
            
            // Save Status Indicator
            when (saveStatus) {
                is SaveStatus.Saving -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is SaveStatus.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "✓ Profile saved successfully",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                is SaveStatus.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: ${(saveStatus as SaveStatus.Error).message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "CrowdLink v0.6.0\nOffline friend-finding for crowded events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}