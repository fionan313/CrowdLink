package com.fyp.crowdlink.data.mesh

import android.content.SharedPreferences
import com.fyp.crowdlink.domain.model.MeshMessage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MeshRoutingEngineTest {

    private lateinit var cache: SeenMessageCache
    private lateinit var engine: MeshRoutingEngine
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)

    @Before
    fun setup() {
        cache = SeenMessageCache()
        // Default to relay enabled
        every { sharedPreferences.getBoolean("mesh_relay", true) } returns true
        
        engine = MeshRoutingEngine(cache, sharedPreferences).apply {
            localDeviceId = "device-local"
        }
    }

    @Test
    fun message_addressed_to_local_device_is_delivered() = runTest {
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
    fun duplicate_message_is_dropped() = runTest {
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
    fun message_with_ttl_zero_is_dropped() = runTest {
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
    fun ttl_decrements_on_relay() = runTest {
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
    fun seen_cache_prevents_reprocessing() = runTest {
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

    @Test
    fun relay_is_dropped_when_mesh_relay_setting_is_disabled() = runTest {
        every { sharedPreferences.getBoolean("mesh_relay", true) } returns false
        
        var relayCount = 0
        engine.onRelay = { relayCount++ }

        val msg = MeshMessage(
            senderId = "device-a",
            recipientId = "device-b",
            payload = "test".toByteArray(),
            ttl = 5
        )

        repeat(20) {
            val fresh = msg.copy(messageId = UUID.randomUUID())
            engine.processIncoming(fresh)
        }

        assertEquals(0, relayCount)
    }
}
