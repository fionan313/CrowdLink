package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

/**
 * FriendRepository
 *
 * This interface defines the contract for accessing and managing friend data.
 * It provides methods for CRUD operations on the friends database, as well as
 * tracking their real-time status (e.g., last seen).
 */
interface FriendRepository {
    
    /**
     * Retrieves a stream of all paired friends.
     * 
     * @return A Flow emitting the list of friends whenever the underlying data changes.
     */
    fun getAllFriends(): Flow<List<Friend>>
    
    /**
     * Retrieves a specific friend by their unique device ID.
     *
     * @param deviceId The unique identifier of the friend.
     * @return The Friend object if found, or null otherwise.
     */
    suspend fun getFriendById(deviceId: String): Friend?
    
    /**
     * Adds a new friend to the repository or updates an existing one.
     *
     * @param friend The Friend object to store.
     */
    suspend fun addFriend(friend: Friend)
    
    /**
     * Removes a specific friend from the repository.
     *
     * @param friend The Friend object to remove.
     */
    suspend fun removeFriend(friend: Friend)
    
    /**
     * Removes a friend from the repository using their device ID.
     *
     * @param deviceId The unique identifier of the friend to remove.
     */
    suspend fun removeFriendById(deviceId: String)
    
    /**
     * Checks if a friend with the given device ID is already paired.
     *
     * @param deviceId The unique identifier to check.
     * @return True if the friend exists in the repository, false otherwise.
     */
    suspend fun isFriendPaired(deviceId: String): Boolean
    
    /**
     * Updates the timestamp of when a friend was last seen.
     *
     * @param deviceId The unique identifier of the friend.
     * @param timestamp The time (in milliseconds) when the friend was last detected.
     */
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)
}
