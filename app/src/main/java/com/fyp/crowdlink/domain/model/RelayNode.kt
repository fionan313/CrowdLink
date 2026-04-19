package com.fyp.crowdlink.domain.model

/**
 * RelayNode
 *
 * Represents a nearby CrowdLink device acting as a relay node on the BLE mesh.
 * Discovered during scanning and used by the routing layer to identify potential
 * next hops for store-and-forward packet delivery.
 */
data class RelayNode(
    val deviceId: String,           // Unique device identifier broadcast in the BLE advertisement
    val name: String,               // Human-readable label, e.g. "CrowdLink-Relay-01"
    val rssi: Int,                  // Received signal strength in dBm, used for distance estimation
    val isConnected: Boolean = false, // Whether an active GATT connection is currently open to this node
    val lastSeen: Long = System.currentTimeMillis() // Unix epoch time (ms) of the most recent scan result
)