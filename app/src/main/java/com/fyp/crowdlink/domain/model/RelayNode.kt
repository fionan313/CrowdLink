package com.fyp.crowdlink.domain.model

data class RelayNode(
    val deviceId: String,           // MAC address
    val name: String,               // "CrowdLink-Relay-01"
    val rssi: Int,                  // Signal strength
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)