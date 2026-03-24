package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Message
 *
 * This entity represents a peer-to-peer message.
 * It is stored in the local database for chat history.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String = UUID.randomUUID().toString(),
    val senderId: String,       // Device ID of the sender
    val receiverId: String,     // Device ID of the receiver
    val content: String,        // Message text
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByMe: Boolean,    // To distinguish in UI
    val deliveryStatus: MessageStatus = MessageStatus.PENDING,
    val ttl: Int = 5,
    val hopCount: Int = 0,
    val transportType: TransportType,
    val relayNodes: String = ""
)

enum class MessageStatus {
    PENDING, SENT, DELIVERED, FAILED
}

enum class TransportType {
    BLE, WIFI, ESP32, MESH
}
