package com.fyp.crowdlink.domain.model

/**
 * DeviceLocation
 *
 * Represents the last known geographical position of a device, received over the BLE mesh
 * and cached locally for use by the compass and map screens. Each field maps directly to
 * the 29-byte binary payload produced by [LocationMessageSerialiser].
 */
data class DeviceLocation(
    val deviceId: String,           // The unique identifier of the device this location belongs to
    val latitude: Double,           // WGS-84 latitude in degrees
    val longitude: Double,          // WGS-84 longitude in degrees
    val accuracy: Float,            // Horizontal accuracy of the fix in metres, as reported by the sender's GPS provider
    val timestamp: Long = System.currentTimeMillis() // Unix epoch time (ms) at which the fix was recorded on the sending device
)