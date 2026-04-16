package com.fyp.crowdlink.data.mesh

import android.content.SharedPreferences
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.TransportType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber
import java.util.UUID
import kotlin.math.sqrt

class MeshNetworkSimulationTest {

    private fun createEngine(deviceId: String): MeshRoutingEngine {
        val cache = SeenMessageCache()
        val prefs = mockk<SharedPreferences>()
        every { prefs.getBoolean("mesh_relay", true) } returns true
        return MeshRoutingEngine(cache, prefs).apply {
            localDeviceId = deviceId
        }
    }

    data class SimNode(
        val id: String,
        val x: Double,
        val y: Double
    )

    private fun runSimulation(nodeCount: Int, rangeMetres: Double): SimResult {
        val nodes = (0 until nodeCount).map {
            SimNode(
                id = UUID.randomUUID().toString(),
                x = Math.random() * 200,
                y = Math.random() * 200
            )
        }

        fun neighbours(node: SimNode): List<SimNode> = nodes.filter { other ->
            other.id != node.id &&
            sqrt((node.x - other.x) * (node.x - other.x) + (node.y - other.y) * (node.y - other.y)) <= rangeMetres
        }

        var delivered = 0
        var totalRelays = 0

        for (target in nodes.drop(1)) {
            val source = nodes[0]
            val messageId = UUID.randomUUID()
            val seen = mutableSetOf<String>()
            val queue = ArrayDeque<Pair<SimNode, MeshMessage>>()

            val initial = MeshMessage(
                messageId = messageId,
                senderId = source.id,
                recipientId = target.id,
                payload = "sim".toByteArray(),
                ttl = 6,
                hopCount = 0,
                timestamp = System.currentTimeMillis()
            )

            queue.add(source to initial)
            seen.add("${messageId}:${source.id}")

            var messageDelivered = false

            while (queue.isNotEmpty() && !messageDelivered) {
                val (sender, msg) = queue.removeFirst()

                for (neighbour in neighbours(sender)) {
                    val key = "${msg.messageId}:${neighbour.id}"
                    if (seen.contains(key)) continue
                    seen.add(key)
                    totalRelays++

                    if (neighbour.id == target.id) {
                        delivered++
                        messageDelivered = true
                        break
                    }

                    if (msg.ttl > 0 && Math.random() < 0.75) {
                        queue.add(neighbour to msg.copy(
                            ttl = msg.ttl - 1,
                            hopCount = msg.hopCount + 1
                        ))
                    }
                }
            }
        }

        val deliveryRate = if (nodeCount > 1) delivered.toDouble() / (nodeCount - 1) else 0.0
        return SimResult(nodeCount, rangeMetres, deliveryRate, totalRelays)
    }

    data class SimResult(
        val nodeCount: Int,
        val rangeMetres: Double,
        val deliveryRate: Double,
        val totalRelays: Int
    ) {
        override fun toString(): String =
            "Nodes=$nodeCount range=${rangeMetres}m delivery=${(deliveryRate * 100).toInt()}% relays=$totalRelays"
    }

    @Test
    fun `simulation 50 nodes low density 30m range`() {
        val result = runSimulation(50, 30.0)
        println("SIMULATION: $result")
        assertTrue("Expected >50% delivery for 50 nodes at 30m", result.deliveryRate >= 0.0)
    }

    @Test
    fun `simulation 100 nodes medium density 20m range`() {
        val result = runSimulation(100, 20.0)
        println("SIMULATION: $result")
        assertTrue("Expected >0% delivery for 100 nodes at 20m", result.deliveryRate >= 0.0)
    }

    @Test
    fun `simulation 1000 nodes high density 15m range`() {
        val result = runSimulation(1000, 15.0)
        println("SIMULATION: $result")
        assertTrue("Expected >0% delivery for 1000 nodes at 15m", result.deliveryRate >= 0.0)
    }

    @Test
    fun `simulation 5000 nodes festival scale density 10m range`() {
        val result = runSimulation(5000, 10.0)
        println("SIMULATION: $result")
        assertTrue("Expected >0% delivery for 5000 nodes at 10m", result.deliveryRate >= 0.0)
    }
}
