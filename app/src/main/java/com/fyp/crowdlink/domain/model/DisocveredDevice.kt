package com.fyp.crowdlink.domain.model

data class DiscoveredDevice(
    val deviceId: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val estimatedDistance: Double = -1.0
)