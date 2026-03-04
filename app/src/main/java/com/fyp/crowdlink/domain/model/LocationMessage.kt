package com.fyp.crowdlink.domain.model

/**
 * LocationMessage
 *
 * Represents a location update message to be sent over the mesh network.
 */
data class LocationMessage(
    val type: Byte = 0x03,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
