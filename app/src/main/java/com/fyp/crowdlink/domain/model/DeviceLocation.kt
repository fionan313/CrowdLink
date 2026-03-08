package com.fyp.crowdlink.domain.model

/**
 * DeviceLocation
 *
 * Represents a geographical location of a device.
 */
data class DeviceLocation(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)
