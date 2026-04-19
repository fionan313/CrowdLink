package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.data.local.entity.RelayMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * RelayMessageDao
 *
 * Data Access Object (DAO) for the [RelayMessageEntity] entity.
 * This interface defines the database interactions for managing the store-and-forward
 * relay queue. Messages are held here temporarily until they can be forwarded on the
 * mesh, or until their TTL expires and they are purged.
 */
@Dao
interface RelayMessageDao {

    /**
     * Inserts a message into the relay queue.
     * If a message with the same ID already exists, the insert is silently ignored
     * to prevent duplicate forwarding.
     *
     * @param message The [RelayMessageEntity] to enqueue.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: RelayMessageEntity)

    /**
     * Retrieves all messages that have not yet expired, ordered by arrival time (oldest first).
     * Oldest messages are forwarded first to respect message ordering across the mesh.
     *
     * @param now The current time in milliseconds. Defaults to the system clock.
     * @return A Flow emitting the active relay queue whenever the table changes.
     */
    @Query("SELECT * FROM relay_messages WHERE expiresAt > :now ORDER BY timestamp ASC")
    fun getActiveRelayQueue(now: Long = System.currentTimeMillis()): Flow<List<RelayMessageEntity>>

    /**
     * Removes a specific message from the relay queue by its ID.
     * Called once a message has been successfully forwarded and no longer needs to be held.
     *
     * @param messageId The unique identifier of the message to remove.
     */
    @Query("DELETE FROM relay_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: String)

    /**
     * Deletes all messages whose expiry time has passed.
     * Called periodically to prevent the relay queue from growing unbounded.
     *
     * @param now The current time in milliseconds. Defaults to the system clock.
     */
    @Query("DELETE FROM relay_messages WHERE expiresAt <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())

    /**
     * Returns the number of messages currently in the relay queue that have not expired.
     * Useful for diagnostics and capping queue size to prevent memory pressure.
     *
     * @param now The current time in milliseconds. Defaults to the system clock.
     * @return The count of active (non-expired) relay messages.
     */
    @Query("SELECT COUNT(*) FROM relay_messages WHERE expiresAt > :now")
    suspend fun activeCount(now: Long = System.currentTimeMillis()): Int
}