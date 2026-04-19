package com.fyp.crowdlink.domain.model

/**
 * SosAlertData
 *
 * Represents a received SOS alert from a paired friend, parsed from either an encrypted
 * (0xFF) or plaintext (0x05) BLE payload. Passed to [MeshNotificationManager] to trigger
 * the full-screen notification, alarm audio, vibration and TTS announcement, and to the
 * SOS alert screen to display the sender's location and navigation options.
 */
data class SosAlertData(
    val friendId: String,       // Device ID of the sender, used to open their chat thread and locate them on the map
    val senderName: String,     // Display name shown in the notification and alert screen
    val latitude: Double?,      // Last known GPS latitude of the sender, null if unavailable at send time
    val longitude: Double?,     // Last known GPS longitude of the sender, null if unavailable at send time
    val receivedAt: Long        // Unix epoch time (ms) at which this device received the alert
)