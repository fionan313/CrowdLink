package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * UserProfileDao
 *
 * Data Access Object (DAO) for the [UserProfile] entity.
 * This interface defines the database interactions required to manage the user's own profile.
 * Since there is only one user profile for the app, queries generally target a specific ID (e.g., id = 1).
 */
@Dao
interface UserProfileDao {
    
    /**
     * Observes the user profile for changes.
     *
     * @return A Flow that emits the UserProfile (or null) whenever the table is updated.
     */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>
    
    /**
     * Retrieves the current user profile once.
     *
     * @return The UserProfile object if it exists, or null otherwise.
     */
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfile?
    
    /**
     * Inserts or updates the user profile in the database.
     * If a profile with the same ID already exists, it will be replaced.
     *
     * @param profile The UserProfile to save.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
    
    /**
     * Updates an existing user profile.
     *
     * @param profile The UserProfile with updated fields.
     */
    @Update
    suspend fun updateUserProfile(profile: UserProfile)
    
    /**
     * Deletes all user profiles from the table.
     * Effectively clears the local user data.
     */
    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()
}
