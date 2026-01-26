package com.fyp.crowdlink.domain.model

/**
 * NearbyFriend
 *
 * This data class represents a friend that has been detected nearby via Bluetooth Low Energy (BLE).
 * It combines persistent data (like display name) with transient scanning data (like RSSI and distance).
 */
data class NearbyFriend(
    val deviceId: String,           // Unique identifier for the friend's device
    val displayName: String,        // Friendly name stored in the local database
    val rssi: Int,                  // Received Signal Strength Indicator (signal strength)
    val estimatedDistance: Double,  // Estimated distance in meters based on RSSI
    val lastSeen: Long              // Timestamp of the most recent BLE discovery
)
