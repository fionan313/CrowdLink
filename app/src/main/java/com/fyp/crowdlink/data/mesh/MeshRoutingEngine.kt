package com.fyp.crowdlink.data.mesh

import android.util.Log
import com.fyp.crowdlink.domain.model.MeshMessage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MeshRoutingEngine @Inject constructor(
    private val seenMessageCache: SeenMessageCache
) {

    // Set this from outside — the local device's ID
    var localDeviceId: String = ""

    // Callbacks wired up by the BLE layer or Repository
    var onMessageForMe: (suspend (MeshMessage) -> Unit)? = null
    var onRelay: (suspend (MeshMessage) -> Unit)? = null

    suspend fun processIncoming(message: MeshMessage) {
        // 1. Duplicate check
        if (seenMessageCache.hasSeenMessage(message.messageId)) {
            Log.d(TAG, "DROP duplicate: ${message.messageId}")
            return
        }
        seenMessageCache.markAsSeen(message.messageId)

        // 2. Is this message for me?
        Log.d(TAG, "COMPARE recipientId=${message.recipientId} localDeviceId=$localDeviceId")
        if (message.recipientId == localDeviceId) {
            Log.d(TAG, "DELIVER to self: ${message.messageId}")
            onMessageForMe?.invoke(message)
            return
        }

        // 3. TTL check
        if (message.ttl <= 0) {
            Log.d(TAG, "DROP ttl=0: ${message.messageId}")
            return
        }

        // 4. Probabilistic relay — 75% chance to forward
        if (Random.nextFloat() > RELAY_PROBABILITY) {
            Log.d(TAG, "DROP probabilistic: ${message.messageId}")
            return
        }

        // 5. Relay — decrement TTL, increment hop count
        val relayed = message.copy(
            ttl = message.ttl - 1,
            hopCount = message.hopCount + 1
        )
        Log.d(TAG, 
            "RELAY messageId=${relayed.messageId} ttl=${relayed.ttl} hop=${relayed.hopCount}"
        )
        onRelay?.invoke(relayed)
    }

    fun createOutbound(
        senderId: String,
        recipientId: String,
        payload: ByteArray
    ): MeshMessage {
        val message = MeshMessage(
            messageId = UUID.randomUUID(),
            senderId = senderId,
            recipientId = recipientId,
            payload = payload,
            ttl = DEFAULT_TTL,
            hopCount = 0
        )
        // Mark our own message as seen so we don't relay it back to ourselves
        seenMessageCache.markAsSeen(message.messageId)
        return message
    }

    companion object {
        private const val TAG = "MeshRouting"
        private const val RELAY_PROBABILITY = 0.75f
        private const val DEFAULT_TTL = 6
    }
}
