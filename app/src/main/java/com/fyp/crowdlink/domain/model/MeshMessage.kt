package com.fyp.crowdlink.domain.model

import java.util.UUID

/**
 * MeshMessage
 *
 * Represents a single packet travelling across the BLE mesh network. Contains both
 * routing metadata in the header and an encrypted payload in the body. The binary
 * layout of these fields is defined in [MeshMessageSerialiser], which packs them
 * into a 512-byte frame for transmission over BLE GATT.
 */
data class MeshMessage(
    val messageId: UUID = UUID.randomUUID(), // Unique identifier used for deduplication in [SeenMessageCache]
    val senderId: String,                    // Device ID of the originating device
    val recipientId: String,                 // Device ID of the intended recipient — relay nodes read this without decrypting
    val payload: ByteArray,                  // AES-256-GCM encrypted body, max 412 bytes
    val ttl: Int = 6,                        // Hops remaining before the packet is dropped — decremented on each relay
    val timestamp: Long = System.currentTimeMillis(), // Unix epoch time (ms) at send time, used for chat ordering
    val hopCount: Int = 0                    // Number of hops taken so far, incremented on each relay
) {
    // equality and hashing are based on messageId alone —
    // ByteArray's default equals() compares by reference, which would break deduplication
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshMessage) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}