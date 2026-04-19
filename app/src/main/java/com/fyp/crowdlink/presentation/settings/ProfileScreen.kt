package com.fyp.crowdlink.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions

/**
 * ProfileScreen
 *
 * Allows the user to update their display name, optional phone number and status message.
 * The display name is the identity shown to paired friends on the mesh. Local state is
 * initialised from the persisted [UserProfile] via a [LaunchedEffect] and committed on
 * tapping the save icon in the top bar. The save icon is disabled until a display name
 * is entered. Save progress is reflected inline via [SaveStatus].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    // populate fields from Room once the profile loads
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
                title = { Text("User Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // save button disabled until a non-blank display name is present
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
            Text(text = "Your Profile", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "This information will be shared when pairing with friends",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // required - shown to friends during pairing and on the discovery screen
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Required - shown to friends") }
            )

            // optional - stored locally, not transmitted over the mesh
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = { Text("Optional - for emergency contact") }
            )

            OutlinedTextField(
                value = statusMessage,
                onValueChange = { statusMessage = it },
                label = { Text("Status Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                supportingText = { Text("Optional - e.g., 'At Electric Picnic 2025'") }
            )

            // inline save feedback - progress bar, success card or error card
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
                            text = "Profile saved successfully",
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
        }
    }
}