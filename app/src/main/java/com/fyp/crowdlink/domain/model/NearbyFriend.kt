package com.fyp.crowdlink.domain.model

data class NearbyFriend(
    val deviceId: String,
    val displayName: String,  // From Friend table
    val rssi: Int,  // From BLE scan
    val estimatedDistance: Double,  // From BLE scan
    val lastSeen: Long  // From BLE scan
)