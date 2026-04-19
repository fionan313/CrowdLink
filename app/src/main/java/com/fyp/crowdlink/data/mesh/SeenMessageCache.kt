package com.fyp.crowdlink.data.mesh

import java.util.UUID
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SeenMessageCache
 *
 * Tracks message UUIDs that this device has already processed, preventing the same
 * packet from being relayed or delivered more than once. Bounded at [MAX_SIZE] entries
 * using a LinkedHashSet so that the oldest UUID is evicted first when the cap is reached.
 */
@Singleton
class SeenMessageCache @Inject constructor() {

    // LinkedHashMap gives insertion-order eviction - oldest entry goes first when full
    private val cache: MutableSet<UUID> = Collections.synchronizedSet(
        object : LinkedHashSet<UUID>() {
            override fun add(element: UUID): Boolean {
                if (size >= MAX_SIZE) {
                    remove(iterator().next()) // evict oldest
                }
                return super.add(element)
            }
        }
    )

    fun hasSeenMessage(messageId: UUID): Boolean = cache.contains(messageId)

    fun markAsSeen(messageId: UUID) {
        cache.add(messageId)
    }

    companion object {
        private const val MAX_SIZE = 200 // cap at 200 UUIDs to bound memory use
    }
}