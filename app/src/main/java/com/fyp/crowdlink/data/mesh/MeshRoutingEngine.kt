package com.fyp.crowdlink.data.mesh

import android.content.SharedPreferences
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.TransportType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * MeshRoutingEngine
 *
 * Core routing logic for the BLE mesh. Processes every incoming packet and decides
 * whether to deliver it locally, relay it, or drop it. Outbound messages are also
 * created here to ensure they enter the seen cache immediately, preventing the
 * originating device from relaying its own packets back.
 */
@Singleton
class MeshRoutingEngine @Inject constructor(
    private val seenMessageCache: SeenMessageCache,
    private val sharedPreferences: SharedPreferences
) {

    var localDeviceId: String = ""

    // Wired up by BleService - called when a message is addressed to this device
    var onMessageForMe: (suspend (MeshMessage, TransportType) -> Unit)? = null

    // Wired up by BLEScanner - called when a packet should be forwarded to nearby peers
    var onRelay: (suspend (MeshMessage) -> Unit)? = null

    /**
     * Processes an incoming mesh packet through the routing pipeline:
     * duplicate check -> destination check -> TTL check -> relay setting -> probabilistic forward.
     */
    suspend fun processIncoming(message: MeshMessage, transportType: TransportType = TransportType.MESH) {
        // 1. Drop if already seen - prevents infinite loops in the mesh
        if (seenMessageCache.hasSeenMessage(message.messageId)) {
            Timber.tag(TAG).d("DROP duplicate: ${message.messageId}")
            return
        }
        seenMessageCache.markAsSeen(message.messageId)

        // 2. Deliver locally if this device is the intended recipient
        Timber.tag(TAG).d("COMPARE recipientId=${message.recipientId} localDeviceId=$localDeviceId via $transportType")
        if (message.recipientId == localDeviceId) {
            Timber.tag(TAG).d("DELIVER to self: ${message.messageId}")
            onMessageForMe?.invoke(message, transportType)
            return
        }

        // 3. Drop if TTL is exhausted
        if (message.ttl <= 0) {
            Timber.tag(TAG).d("DROP ttl=0: ${message.messageId}")
            return
        }

        // 4. Drop if the user has disabled mesh relay in settings
        val relayEnabled = sharedPreferences.getBoolean("mesh_relay", true)
        if (!relayEnabled) {
            Timber.tag(TAG).d("DROP relay disabled in settings: ${message.messageId}")
            return
        }

        // 5. Probabilistic forwarding - 75% chance to relay, reduces redundant broadcasts
        if (Random.nextFloat() > RELAY_PROBABILITY) {
            Timber.tag(TAG).d("DROP probabilistic: ${message.messageId}")
            return
        }

        // 6. Forward with decremented TTL and incremented hop count
        val relayed = message.copy(
            ttl = message.ttl - 1,
            hopCount = message.hopCount + 1
        )
        Timber.tag(TAG).d("RELAY messageId=${relayed.messageId} ttl=${relayed.ttl} hop=${relayed.hopCount}")
        onRelay?.invoke(relayed)
    }

    /**
     * Builds a new outbound [MeshMessage] and marks it as seen immediately,
     * so the originating device does not relay its own packet back into the mesh.
     */
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
        seenMessageCache.markAsSeen(message.messageId)
        return message
    }

    companion object {
        private const val TAG = "MeshRouting"
        private const val RELAY_PROBABILITY = 0.75f
        private const val DEFAULT_TTL = 6
    }
}