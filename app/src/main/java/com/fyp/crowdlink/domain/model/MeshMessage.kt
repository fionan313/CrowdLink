package com.fyp.crowdlink.domain.model

import java.util.UUID

data class MeshMessage(
    val messageId: UUID = UUID.randomUUID(),
    val senderId: String,
    val recipientId: String,
    val payload: ByteArray,
    val ttl: Int = 6,
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0
) {
    // ByteArray needs manual equals/hashCode or UUID comparisons will break
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshMessage) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}