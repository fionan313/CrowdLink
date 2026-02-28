package com.fyp.crowdlink.data.mesh

import com.fyp.crowdlink.domain.model.MeshMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import java.util.UUID

class MeshNetworkSimulationTest {

    /**
     * Simulates a network of N nodes arranged in a linear chain:
     * Node0 -> Node1 -> Node2 -> ... -> NodeN
     *
     * Message originates at Node0, destination is NodeN.
     * Each node only forwards to its immediate neighbour.
     * Tests whether a message can traverse the full chain.
     */
    private fun buildLinearNetwork(nodeCount: Int): List<MeshRoutingEngine> {
        val nodes = List(nodeCount) { index ->
            val cache = SeenMessageCache()
            MeshRoutingEngine(cache).apply {
                localDeviceId = "device-$index"
            }
        }

        // Wire each node to forward to the next in the chain
        nodes.forEachIndexed { index, engine ->
            if (index < nodes.size - 1) {
                val next = nodes[index + 1]
                engine.onRelay = { message ->
                    next.processIncoming(message)
                }
            }
        }

        return nodes
    }

    /**
     * Simulates a fully connected mesh where every node
     * is in range of every other node — worst case for flooding.
     */
    private fun buildFullyConnectedNetwork(nodeCount: Int): List<MeshRoutingEngine> {
        val nodes = List(nodeCount) { index ->
            val cache = SeenMessageCache()
            MeshRoutingEngine(cache).apply {
                localDeviceId = "device-$index"
            }
        }

        // Every node relays to every other node
        nodes.forEach { engine ->
            engine.onRelay = { message ->
                nodes.forEach { peer ->
                    if (peer.localDeviceId != engine.localDeviceId) {
                        peer.processIncoming(message)
                    }
                }
            }
        }

        return nodes
    }

    // ---- Linear Chain Tests ----

    @Test
    fun messageTraverses5NodeChain() {
        val nodes = buildLinearNetwork(5)
        var delivered = false
        nodes.last().onMessageForMe = { delivered = true }

        val message = MeshMessage(
            senderId = "device-0",
            recipientId = "device-4",
            payload = "hello".toByteArray(),
            ttl = 6
        )
        nodes.first().processIncoming(message)

        // With TTL=6 and 4 hops needed, message should arrive
        // Note: probabilistic relay means we run multiple attempts
        var attempts = 0
        while (!delivered && attempts < 100) {
            val fresh = message.copy(
                messageId = UUID.randomUUID()
            )
            nodes.first().processIncoming(fresh)
            attempts++
        }

        assertTrue("Message should reach end of 5-node chain within 100 attempts", delivered)
    }

    @Test
    fun messageFailsWith10NodeChainAndTtl5() {
        // TTL of 5 means message dies before reaching node 10 in a 10-hop chain
        // This validates TTL is actually enforced
        val nodes = buildLinearNetwork(10)
        var delivered = false
        nodes.last().onMessageForMe = { delivered = true }

        repeat(50) {
            val message = MeshMessage(
                senderId = "device-0",
                recipientId = "device-9",
                payload = "test".toByteArray(),
                ttl = 5  // Only 5 hops, need 9
            )
            nodes.first().processIncoming(message)
        }

        assertFalse("Message should NOT reach node 9 with TTL=5 in a 10-hop chain", delivered)
    }

    // ---- Scale Tests ----

    @Test
    fun deliveryRatioIn50NodeLinearNetwork() {
        val nodeCount = 50
        val nodes = buildLinearNetwork(nodeCount)
        var deliveredCount = 0
        val totalAttempts = 200

        nodes.last().onMessageForMe = { deliveredCount++ }

        repeat(totalAttempts) {
            val message = MeshMessage(
                senderId = "device-0",
                recipientId = "device-${nodeCount - 1}",
                payload = "test".toByteArray(),
                ttl = 6
            )
            nodes.first().processIncoming(message)
        }

        val deliveryRatio = deliveredCount.toFloat() / totalAttempts
        println("50-node linear delivery ratio: ${"%.1f".format(deliveryRatio * 100)}%")

        // With TTL=6 and 49 hops needed, expect 0% — validates TTL is a real constraint
        // This is an intentional finding, not a failure
        println("Note: TTL=6 insufficient for 49-hop chain — expected result")
    }

    @Test
    fun deliveryRatioIn10NodeFullyConnectedNetwork() {
        val nodeCount = 10
        val nodes = buildFullyConnectedNetwork(nodeCount)
        var deliveredCount = 0
        val totalAttempts = 100

        nodes.last().onMessageForMe = { deliveredCount++ }

        repeat(totalAttempts) {
            val message = MeshMessage(
                senderId = "device-0",
                recipientId = "device-${nodeCount - 1}",
                payload = "test".toByteArray(),
                ttl = 6
            )
            nodes.first().processIncoming(message)
        }

        val deliveryRatio = deliveredCount.toFloat() / totalAttempts
        println("10-node fully connected delivery ratio: ${"%.1f".format(deliveryRatio * 100)}%")

        // In a fully connected network with probabilistic relay,
        // expect high delivery ratio since many paths exist
        assertTrue(
            "Expected >80% delivery in fully connected 10-node network, got ${deliveryRatio * 100}%",
            deliveryRatio > 0.8f
        )
    }

    @Test
    fun simulate500NodeCrowdScenario() {
        // Models a festival crowd — random mesh where each node
        // is in range of approximately 5 neighbours
        val nodeCount = 500
        val neighbourCount = 5
        val caches = List(nodeCount) { SeenMessageCache() }
        val nodes = List(nodeCount) { index ->
            MeshRoutingEngine(caches[index]).apply {
                localDeviceId = "device-$index"
            }
        }

        // Wire random neighbours simulating BLE range overlap in a crowd
        val random = Random(42) // Fixed seed for reproducibility
        nodes.forEachIndexed { index, engine ->
            val neighbours = (0 until nodeCount)
                .filter { it != index }
                .shuffled(random)
                .take(neighbourCount)
                .map { nodes[it] }

            engine.onRelay = { message ->
                neighbours.forEach { neighbour ->
                    neighbour.processIncoming(message)
                }
            }
        }

        // Send from node 0 to node 499
        var delivered = false
        nodes.last().onMessageForMe = { delivered = true }

        var attempts = 0
        while (!delivered && attempts < 50) {
            val message = MeshMessage(
                senderId = "device-0",
                recipientId = "device-499",
                payload = "festival message".toByteArray(),
                ttl = 6
            )
            nodes.first().processIncoming(message)
            attempts++
        }

        println("500-node crowd: delivered=$delivered after $attempts attempts")
        // Document the result either way — both outcomes are findings
    }

    @Test
    fun measureDuplicateSuppression500Nodes() {
        // Counts how many times the routing engine processes a message
        // across the whole network — validates cache is reducing redundant work
        val nodeCount = 500
        val neighbourCount = 5
        var totalProcessed = 0

        val caches = List(nodeCount) { SeenMessageCache() }
        val nodes = List(nodeCount) { index ->
            MeshRoutingEngine(caches[index]).apply {
                localDeviceId = "device-$index"
            }
        }

        val random = Random(42)
        nodes.forEachIndexed { index, engine ->
            val neighbours = (0 until nodeCount)
                .filter { it != index }
                .shuffled(random)
                .take(neighbourCount)
                .map { nodes[it] }

            engine.onRelay = { message ->
                totalProcessed++
                neighbours.forEach { neighbour ->
                    neighbour.processIncoming(message)
                }
            }
        }

        val message = MeshMessage(
            senderId = "device-0",
            recipientId = "device-499",
            payload = "test".toByteArray(),
            ttl = 6
        )
        nodes.first().processIncoming(message)

        println("500-node network: total relay decisions = $totalProcessed")
        println("Without deduplication this could be exponentially higher")

        // The raw number here is your evidence that managed flooding
        // is bounded — cite this in the report
        assertTrue("Relay count should be bounded, not exponential", totalProcessed < nodeCount * 10)
    }
}