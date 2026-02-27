package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.MeshMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MeshMessageSerialiserTest {

    private lateinit var serialiser: MeshMessageSerialiser

    @Before
    fun setup() {
        serialiser = MeshMessageSerialiser()
    }

    @Test
    fun serializeAndDeserializeRoundTrip() {
        val original = MeshMessage(
            senderId = "device-sender-01",
            recipientId = "device-recipient",
            payload = "hello mesh".toByteArray(),
            ttl = 5,
            hopCount = 2
        )

        val bytes = serialiser.serialize(original)
        assertNotNull(bytes)

        val restored = serialiser.deserialize(bytes!!)
        assertNotNull(restored)

        assertEquals(original.messageId, restored!!.messageId)
        assertEquals(original.senderId, restored.senderId)
        assertEquals(original.recipientId, restored.recipientId)
        assertEquals(original.ttl, restored.ttl)
        assertEquals(original.hopCount, restored.hopCount)
        assertArrayEquals(original.payload, restored.payload)
    }

    @Test
    fun emptyPayloadSerializes() {
        val msg = MeshMessage(
            senderId = "a",
            recipientId = "b",
            payload = ByteArray(0)
        )
        val bytes = serialiser.serialize(msg)
        assertNotNull(bytes)
        val restored = serialiser.deserialize(bytes!!)
        assertNotNull(restored)
        assertArrayEquals(ByteArray(0), restored!!.payload)
    }

    @Test
    fun oversizedPayloadReturnsNull() {
        val msg = MeshMessage(
            senderId = "a",
            recipientId = "b",
            payload = ByteArray(500) // over the 452 limit
        )
        val result = serialiser.serialize(msg)
        assertNull(result)
    }

    @Test
    fun corruptBytesReturnNull() {
        val result = serialiser.deserialize(ByteArray(10))
        assertNull(result)
    }

    @Test
    fun senderAndRecipientIdTruncation() {
        // The serialiser uses fixed 16-byte fields for IDs
        val longId = "this-is-a-very-long-id-string"
        val msg = MeshMessage(
            senderId = longId,
            recipientId = "short",
            payload = "test".toByteArray()
        )
        
        val bytes = serialiser.serialize(msg)
        val restored = serialiser.deserialize(bytes!!)
        
        // Should be truncated to first 16 bytes
        assertEquals(longId.take(16), restored!!.senderId)
        assertEquals("short", restored.recipientId)
    }
}
