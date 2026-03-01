package com.fyp.crowdlink.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        ) {

            // ── Profile ──────────────────────────────────────────
            SettingsSectionHeader("Profile")
            SettingsNavigationItem(
                icon = Icons.Default.Person,
                title = "User Profile",
                subtitle = "Display name, phone number, status",
                onClick = onNavigateToProfile
            )

            HorizontalDivider()

            // ── BLE & Mesh ────────────────────────────────────────
            SettingsSectionHeader("BLE & Mesh")
            SettingsToggleItem(
                icon = Icons.Default.Bluetooth,
                title = "Auto-start on launch",
                subtitle = "Begin scanning and advertising when app opens",
                checked = true,   // wire to ViewModel
                onCheckedChange = { }
            )
            SettingsToggleItem(
                icon = Icons.Default.Hub,
                title = "Mesh relay",
                subtitle = "Allow your device to forward messages for others",
                checked = true,
                onCheckedChange = { }
            )
            SettingsInfoItem(
                icon = Icons.Default.Speed,
                title = "Relay probability",
                value = "75%"
            )
            SettingsInfoItem(
                icon = Icons.Default.Repeat,
                title = "Max TTL (hops)",
                value = "5"
            )

            HorizontalDivider()

            // ── LoRa / ESP32 ──────────────────────────────────────
            SettingsSectionHeader("LoRa / Relay Nodes")
            SettingsToggleItem(
                icon = Icons.Default.Router,
                title = "ESP32 relay scanning",
                subtitle = "Scan for nearby CrowdLink relay nodes",
                checked = false,
                onCheckedChange = { }
            )
            SettingsInfoItem(
                icon = Icons.Default.SettingsInputAntenna,
                title = "LoRa frequency",
                value = "868 MHz"
            )

            HorizontalDivider()

            // ── Privacy ───────────────────────────────────────────
            SettingsSectionHeader("Privacy")
            SettingsToggleItem(
                icon = Icons.Default.LocationOff,
                title = "Location sharing",
                subtitle = "Share GPS coordinates with paired friends",
                checked = false,
                onCheckedChange = { }
            )
            SettingsToggleItem(
                icon = Icons.Default.VisibilityOff,
                title = "Ephemeral IDs",
                subtitle = "Rotate BLE identifier periodically",
                checked = true,
                onCheckedChange = { }
            )

            HorizontalDivider()

            // ── About ─────────────────────────────────────────────
            SettingsSectionHeader("About")
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "Version",
                value = "0.7.0"
            )
            SettingsInfoItem(
                icon = Icons.Default.Devices,
                title = "Device ID",
                value = "Tap to copy"   // wire to SharedPreferences
            )
        }
    }
}

// ── Reusable components ───────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}