package com.fyp.crowdlink.domain.model

/**
 * DiscoveredDevice
 *
 * This data class represents a raw device discovered during a Bluetooth Low Energy (BLE) scan.
 * It contains technical scanning data like RSSI and timestamps, used primarily by the
 * BLE scanner and distance estimation logic before being mapped to a user-friendly model.
 */
data class DiscoveredDevice(
    val deviceId: String,           // The unique address or identifier of the BLE device
    val rssi: Int,                  // Received Signal Strength Indicator (raw signal strength in dBm)
    val timestamp: Long = System.currentTimeMillis(), // Time when this specific scan result was received
    val estimatedDistance: Double = -1.0, // Calculated distance in meters based on RSSI (defaults to -1.0 if unknown)
    val lastSeen: Long              // The most recent timestamp this device was seen
)
