package com.fyp.crowdlink.domain.model

/**
 * PairingRequest
 *
 * Represents an incoming pairing request received over BLE GATT from a device that has
 * scanned this device's QR code. The [sharedKey] is present when the sender extracted it
 * from the QR payload — its presence confirms the sender physically scanned the code and
 * allows both devices to synchronise on the same AES-256-GCM key without a second scan.
 */
data class PairingRequest(
    val senderDeviceId: String,             // Device ID extracted from the GATT write payload
    val senderDisplayName: String,          // Display name shown in the pairing confirmation dialogue
    val sharedKey: String? = null,          // Base64-encoded AES-256-GCM key, null if QR was not scanned
    val timestamp: Long = System.currentTimeMillis() // Time the request was received, used to expire stale requests
)