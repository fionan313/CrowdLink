package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Message
 *
 * Represents a peer-to-peer chat message exchanged over the BLE mesh. Persisted in Room
 * so conversation history is available regardless of whether the sender is currently in range.
 * The [senderId] and [receiverId] fields are used to reconstruct conversation threads in
 * [MessageDao], matching on both directions of the exchange.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String = UUID.randomUUID().toString(), // Unique identifier for deduplication across mesh hops
    val senderId: String,       // Device ID of the originating device
    val receiverId: String,     // Device ID of the intended recipient
    val content: String,        // Plaintext message content, decrypted before storage
    val timestamp: Long = System.currentTimeMillis(), // Unix epoch time (ms) used for chat ordering
    val isSentByMe: Boolean,    // Determines message alignment in the chat UI
    val deliveryStatus: MessageStatus = MessageStatus.PENDING,
    val ttl: Int = 5,           // Hops remaining before the packet is dropped
    val hopCount: Int = 0,      // Number of relay hops taken, used for distance estimation in UI
    val transportType: TransportType, // The transport layer the message arrived on
    val relayNodes: String = "" // Comma-separated list of relay device IDs, for diagnostics
)

/**
 * Tracks the delivery state of an outbound message through the mesh.
 */
enum class MessageStatus {
    PENDING,    // Written to Room, not yet handed to the BLE layer
    SENT,       // Handed off to the BLE layer for transmission
    DELIVERED,  // Acknowledged by the recipient
    FAILED      // All relay attempts exhausted
}

/**
 * Identifies which transport layer carried a message or location update.
 * MESH is the primary path. WIFI is retained for the Wi-Fi Direct future work path.
 */
enum class TransportType {
    BLE, WIFI, ESP32, MESH, USB
}