package com.fyp.crowdlink.data.mesh

import java.util.UUID
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeenMessageCache @Inject constructor() {

    // LinkedHashMap gives us insertion-order eviction for the LRU behaviour
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

    fun clear() = cache.clear()

    companion object {
        private const val MAX_SIZE = 200
    }
}