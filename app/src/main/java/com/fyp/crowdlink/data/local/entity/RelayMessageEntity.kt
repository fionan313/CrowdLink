package com.fyp.crowdlink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fyp.crowdlink.domain.model.MeshMessage
import java.util.UUID

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
    val expiresAt: Long = System.currentTimeMillis() + TTL_MS
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelayMessageEntity) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()

    companion object {
        // Messages expire after 30 minutes — no point relaying stale messages
        private const val TTL_MS = 30 * 60 * 1000L
    }
}

fun RelayMessageEntity.toDomain(): MeshMessage = MeshMessage(
    messageId = UUID.fromString(messageId),
    senderId = senderId,
    recipientId = recipientId,
    payload = payload,
    ttl = ttl,
    timestamp = timestamp,
    hopCount = hopCount
)

fun MeshMessage.toEntity(): RelayMessageEntity = RelayMessageEntity(
    messageId = messageId.toString(),
    senderId = senderId,
    recipientId = recipientId,
    payload = payload,
    ttl = ttl,
    timestamp = timestamp,
    hopCount = hopCount
)