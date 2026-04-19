package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.MeshMessage
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MeshMessageSerialiser
 *
 * Serialises and deserialises [MeshMessage] objects into a compact binary format
 * for transmission over BLE. A fixed 100-byte header is followed by a variable-length
 * encrypted payload, keeping the total frame within the 512-byte MTU limit.
 *
 * Packet format (max 512 bytes - BLE MTU limit):
 *
 * [0-15]   messageId (UUID - 2 longs, 16 bytes)
 * [16-51]  senderId (UUID string - 36 bytes, fixed width)
 * [52-87]  recipientId (UUID string - 36 bytes, fixed width)
 * [88]     ttl (1 byte, unsigned)
 * [89]     hopCount (1 byte, unsigned)
 * [90-97]  timestamp (long - 8 bytes)
 * [98-99]  payloadLength (short - 2 bytes)
 * [100+]   payload (encrypted, max 412 bytes)
 *
 * Total header = 100 bytes
 * Max payload  = 412 bytes
 */
@Singleton
class MeshMessageSerialiser @Inject constructor() {

    /**
     * Packs a [MeshMessage] into a byte array for BLE transmission.
     * Returns null if serialisation fails or the payload exceeds [MAX_PAYLOAD_BYTES].
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

            // senderId - pad or truncate to exactly 36 bytes
            buffer.put(message.senderId.toFixedBytes(DEVICE_ID_SIZE))

            // recipientId - pad or truncate to exactly 36 bytes
            buffer.put(message.recipientId.toFixedBytes(DEVICE_ID_SIZE))

            buffer.put(message.ttl.toByte())
            buffer.put(message.hopCount.toByte())
            buffer.putLong(message.timestamp)
            buffer.putShort(payloadSize.toShort())
            buffer.put(message.payload)

            buffer.array()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unpacks a byte array back into a [MeshMessage].
     * Returns null if the frame is too short, the payload length field is invalid,
     * or any other parsing error occurs - preventing a malformed packet from
     * propagating up the stack.
     */
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

            if (payloadLength > MAX_PAYLOAD_BYTES) return null

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
        } catch (_: Exception) {
            null
        }
    }

    // Converts a String to exactly `size` bytes - pads with zeros or truncates
    private fun String.toFixedBytes(size: Int): ByteArray {
        val src = this.toByteArray(Charsets.UTF_8)
        val dst = ByteArray(size)
        src.copyInto(dst, endIndex = minOf(src.size, size))
        return dst
    }

    // Strips trailing zero bytes and converts back to a String
    private fun ByteArray.toTrimmedString(): String {
        val end = indexOfFirst { it == 0.toByte() }.takeIf { it >= 0 } ?: size
        return String(this, 0, end, Charsets.UTF_8)
    }

    companion object {
        const val HEADER_SIZE = 100
        const val DEVICE_ID_SIZE = 36
        const val MAX_PAYLOAD_BYTES = 412  // 512 - 100
    }
}