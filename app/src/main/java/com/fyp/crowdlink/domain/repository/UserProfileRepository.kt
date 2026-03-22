package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * UserProfileRepository
 *
 * This interface defines the contract for managing the local user's profile data.
 * It abstracts the data source (Database/Room) from the domain and UI layers,
 * allowing for retrieval, creation, updating, and deletion of the user's identity.
 */
interface UserProfileRepository {
    
    /**
     * Retrieves the user profile as a stream.
     * 
     * @return A Flow that emits the UserProfile (or null if not set) whenever it changes.
     */
    fun getUserProfile(): Flow<UserProfile?>
    
    /**
     * Retrieves the current user profile immediately as a one-shot operation.
     *
     * @return The UserProfile if it exists, or null otherwise.
     */
    suspend fun getUserProfileOnce(): UserProfile?
    
    /**
     * Saves a new user profile to the repository.
     * 
     * @param profile The UserProfile object to be persisted.
     */
    suspend fun saveUserProfile(profile: UserProfile)
    
    /**
     * Updates the existing user profile with new information.
     *
     * @param profile The UserProfile object containing updated fields.
     */
    suspend fun updateUserProfile(profile: UserProfile)
    
    /**
     * Deletes the user profile from the repository.
     * This is typically used when resetting the app or clearing user data.
     */
    suspend fun deleteUserProfile()

    /**
     * Clears all fields in the user profile.
     */
    suspend fun clearUserProfile()

    /**
     * Retrieves a unique, persistent ID for this device.
     */
    fun getPersistentDeviceId(): String
}
