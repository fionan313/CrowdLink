package com.fyp.crowdlink.data.mesh

import java.util.UUID
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

// tracks which message UUIDs this device has already processed.
// prevents the same packet being relayed or delivered more than once
@Singleton
class SeenMessageCache @Inject constructor() {

    // LinkedHashMap gives insertion-order eviction — oldest entry goes first when full
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