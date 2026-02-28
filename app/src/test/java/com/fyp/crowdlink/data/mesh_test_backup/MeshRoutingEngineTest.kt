package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.MeshMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MeshRoutingEngineTest {

    private lateinit var cache: SeenMessageCache
    private lateinit var engine: MeshRoutingEngine

    @Before
    fun setup() {
        cache = SeenMessageCache()
        engine = MeshRoutingEngine(cache).apply {
            localDeviceId = "device-local"
        }
    }

    @Test
    fun `message addressed to local device is delivered`() {
        var delivered: MeshMessage? = null
        engine.onMessageForMe = { delivered = it }

        val msg = MeshMessage(
            senderId = "device-remote",
            recipientId = "device-local",
            payload = "hello".toByteArray()
        )
        engine.processIncoming(msg)

        assertNotNull(delivered)
        assertEquals(msg.messageId, delivered!!.messageId)
    }

    @Test
    fun `duplicate message is dropped`() {
        var relayCount = 0
        engine.onRelay = { relayCount++ }

        val msg = MeshMessage(
            senderId = "device-a",
            recipientId = "device-b",
            payload = "test".toByteArray(),
            ttl = 5
        )

        engine.processIncoming(msg)
        engine.processIncoming(msg) // second time — should be dropped

        // relayCount could be 0 or 1 due to probabilistic relay,
        // but crucially it should never be 2
        assertTrue(relayCount <= 1)
    }

    @Test
    fun `message with ttl zero is dropped`() {
        var relayed: MeshMessage? = null
        engine.onRelay = { relayed = it }

        val msg = MeshMessage(
            senderId = "device-a",
            recipientId = "device-b",
            payload = "test".toByteArray(),
            ttl = 0
        )
        engine.processIncoming(msg)

        assertNull(relayed)
    }

    @Test
    fun `ttl decrements on relay`() {
        val relayed = mutableListOf<MeshMessage>()
        // Force relay by setting probability — override for test
        engine.onRelay = { relayed.add(it) }

        val msg = MeshMessage(
            senderId = "device-a",
            recipientId = "device-b",
            payload = "test".toByteArray(),
            ttl = 5
        )

        // Run enough times that at least one gets through probabilistic check
        repeat(20) {
            val fresh = msg.copy(messageId = UUID.randomUUID())
            engine.processIncoming(fresh)
        }

        // At least some should have been relayed with ttl=4
        assertTrue(relayed.isNotEmpty())
        assertTrue(relayed.all { it.ttl == 4 })
        assertTrue(relayed.all { it.hopCount == 1 })
    }

    @Test
    fun `seen cache prevents reprocessing`() {
        var deliveryCount = 0
        engine.onMessageForMe = { deliveryCount++ }

        val msg = MeshMessage(
            senderId = "device-a",
            recipientId = "device-local",
            payload = "test".toByteArray()
        )

        engine.processIncoming(msg)
        engine.processIncoming(msg)

        assertEquals(1, deliveryCount)
    }
}