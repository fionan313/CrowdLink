package com.fyp.crowdlink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fyp.crowdlink.domain.model.MeshMessage
import java.util.UUID

/**
 * RelayMessageEntity
 *
 * Represents a mesh packet held in the store-and-forward relay queue.
 * Messages are persisted here until they are forwarded to the next hop
 * or until [expiresAt] is reached, after which they are purged.
 */
@Entity(tableName = "relay_messages")
data class RelayMessageEntity(
    @PrimaryKey
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val payload: ByteArray,
    val ttl: Int,
    val hopCount: Int,
    val timestamp: Long,
    // Default expiry of 30 minutes from insertion time
    val expiresAt: Long = System.currentTimeMillis() + TTL_MS
) {
    // Equality is based on messageId alone to match deduplication behaviour in the mesh engine
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelayMessageEntity) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()

    companion object {
        private const val TTL_MS = 30 * 60 * 1000L
    }
}

// Converts a stored entity back into a domain MeshMessage for processing by the routing engine
fun RelayMessageEntity.toDomain(): MeshMessage = MeshMessage(
    messageId = UUID.fromString(messageId),
    senderId = senderId,
    recipientId = recipientId,
    payload = payload,
    ttl = ttl,
    timestamp = timestamp,
    hopCount = hopCount
)

// Converts an outbound MeshMessage into an entity for persistence in the relay queue
fun MeshMessage.toEntity(): RelayMessageEntity = RelayMessageEntity(
    messageId = messageId.toString(),
    senderId = senderId,
    recipientId = recipientId,
    payload = payload,
    ttl = ttl,
    timestamp = timestamp,
    hopCount = hopCount
)