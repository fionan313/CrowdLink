package com.fyp.crowdlink.data.mesh_backup

import com.fyp.crowdlink.domain.model.MeshMessage
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshMessageSerialiser @Inject constructor() {

    /**
     * Packet format (max 512 bytes — BLE MTU limit):
     *
     * [0-15]   messageId (UUID — 2 longs, 16 bytes)
     * [16-31]  senderId (UUID — 16 bytes)
     * [32-47]  recipientId (UUID — 16 bytes)
     * [48]     ttl (1 byte)
     * [49]     hopCount (1 byte)
     * [50-57]  timestamp (long — 8 bytes)
     * [58-59]  payloadLength (short — 2 bytes)
     * [60+]    payload (remaining bytes, max 452 bytes)
     *
     * Total header = 60 bytes
     * Max payload  = 452 bytes
     */

    fun serialize(message: MeshMessage): ByteArray? {
        return try {
            val payloadSize = message.payload.size
            if (payloadSize > MAX_PAYLOAD_BYTES) {
                throw IllegalArgumentException(
                    "Payload too large: $payloadSize bytes, max is $MAX_PAYLOAD_BYTES"
                )
            }

            val buffer = ByteBuffer.allocate(HEADER_SIZE + payloadSize)

            // messageId as two longs
            buffer.putLong(message.messageId.mostSignificantBits)
            buffer.putLong(message.messageId.leastSignificantBits)

            // senderId — pad or truncate to exactly 16 bytes
            buffer.put(message.senderId.toFixedBytes(DEVICE_ID_SIZE))

            // recipientId — pad or truncate to exactly 16 bytes
            buffer.put(message.recipientId.toFixedBytes(DEVICE_ID_SIZE))

            buffer.put(message.ttl.toByte())
            buffer.put(message.hopCount.toByte())
            buffer.putLong(message.timestamp)
            buffer.putShort(payloadSize.toShort())
            buffer.put(message.payload)

            buffer.array()
        } catch (e: Exception) {
            null
        }
    }

    fun deserialize(bytes: ByteArray): MeshMessage? {
        return try {
            if (bytes.size < HEADER_SIZE) return null

            val buffer = ByteBuffer.wrap(bytes)

            // messageId
            val msb = buffer.getLong()
            val lsb = buffer.getLong()
            val messageId = UUID(msb, lsb)

            // senderId
            val senderIdBytes = ByteArray(DEVICE_ID_SIZE)
            buffer.get(senderIdBytes)
            val senderId = senderIdBytes.toTrimmedString()

            // recipientId
            val recipientIdBytes = ByteArray(DEVICE_ID_SIZE)
            buffer.get(recipientIdBytes)
            val recipientId = recipientIdBytes.toTrimmedString()

            val ttl = buffer.get().toInt() and 0xFF   // unsigned
            val hopCount = buffer.get().toInt() and 0xFF
            val timestamp = buffer.getLong()
            val payloadLength = buffer.getShort().toInt() and 0xFFFF

            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) return null

            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            MeshMessage(
                messageId = messageId,
                senderId = senderId,
                recipientId = recipientId,
                payload = payload,
                ttl = ttl,
                timestamp = timestamp,
                hopCount = hopCount
            )
        } catch (e: Exception) {
            null
        }
    }

    // Converts a String to exactly `size` bytes — pads with zeros or truncates
    private fun String.toFixedBytes(size: Int): ByteArray {
        val src = this.toByteArray(Charsets.UTF_8)
        val dst = ByteArray(size)
        src.copyInto(dst, endIndex = minOf(src.size, size))
        return dst
    }

    // Strips trailing zero bytes and converts back to String
    private fun ByteArray.toTrimmedString(): String {
        val end = indexOfFirst { it == 0.toByte() }.takeIf { it >= 0 } ?: size
        return String(this, 0, end, Charsets.UTF_8)
    }

    companion object {
        const val HEADER_SIZE = 60
        const val DEVICE_ID_SIZE = 16
        const val MAX_PAYLOAD_BYTES = 452  // 512 - 60 header bytes
    }
}