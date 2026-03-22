package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

/**
 * FriendDao
 *
 * Data Access Object (DAO) for the [Friend] entity.
 * This interface defines the database interactions for managing the list of paired friends,
 * including retrieval, insertion, deletion, and status updates.
 */
@Dao
interface FriendDao {
    
    /**
     * Retrieves all friends from the database, ordered by pairing time (newest first).
     *
     * @return A Flow emitting the list of friends whenever the table changes.
     */
    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    fun getAllFriends(): Flow<List<Friend>>

    /**
     * Retrieves all friends immediately as a one-shot operation.
     * Useful for internal checks where a Flow is not needed.
     *
     * @return A list of all paired friends.
     */
    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    suspend fun getPairedFriends(): List<Friend>
    
    /**
     * Finds a specific friend by their device ID.
     *
     * @param deviceId The unique identifier of the friend.
     * @return The Friend object if found, or null otherwise.
     */
    @Query("SELECT * FROM friends WHERE deviceId = :deviceId")
    suspend fun getFriendById(deviceId: String): Friend?
    
    /**
     * Inserts a new friend or updates an existing one.
     * If a friend with the same device ID exists, their record is replaced.
     *
     * @param friend The Friend object to save.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)
    
    /**
     * Deletes a specific friend from the database.
     * Used for unpairing.
     *
     * @param friend The Friend object to remove.
     */
    @Delete
    suspend fun deleteFriend(friend: Friend)
    
    /**
     * Deletes a friend based on their device ID.
     * An alternative unpairing method when the full Friend object isn't available.
     *
     * @param deviceId The unique identifier of the friend to remove.
     */
    @Query("DELETE FROM friends WHERE deviceId = :deviceId")
    suspend fun deleteFriendById(deviceId: String)

    /**
     * Deletes all friends from the database.
     */
    @Query("DELETE FROM friends")
    suspend fun deleteAll()
    
    /**
     * Checks if a friend exists in the database.
     *
     * @param deviceId The unique identifier to check.
     * @return The count of rows matching the device ID (1 if paired, 0 if not).
     */
    @Query("SELECT COUNT(*) FROM friends WHERE deviceId = :deviceId")
    suspend fun isFriendPaired(deviceId: String): Int
    
    /**
     * Updates the 'last seen' timestamp for a specific friend.
     * This is used to track when a friend was last detected nearby.
     *
     * @param deviceId The unique identifier of the friend.
     * @param timestamp The new timestamp value.
     */
    @Query("UPDATE friends SET lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)
}
