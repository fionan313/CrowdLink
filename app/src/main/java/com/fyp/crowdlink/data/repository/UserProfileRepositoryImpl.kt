package com.fyp.crowdlink.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.model.UserProfile
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserProfileRepositoryImpl
 *
 * This class implements the [UserProfileRepository] interface.
 * It serves as the data layer implementation for managing the local user's profile,
 * delegating database operations to the [UserProfileDao].
 */
@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val sharedPreferences: SharedPreferences
) : UserProfileRepository {
    
    /**
     * Retrieves the user profile as a Flow, allowing the UI to react to changes in real-time.
     */
    override fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getUserProfile()
    }
    
    /**
     * Retrieves the current user profile immediately (one-shot).
     * Useful for logic that needs the current state without observing future updates.
     */
    override suspend fun getUserProfileOnce(): UserProfile? {
        return userProfileDao.getUserProfileOnce()
    }
    
    /**
     * Persists a new user profile to the database.
     */
    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertUserProfile(profile)
    }
    
    /**
     * Updates the existing user profile in the database.
     */
    override suspend fun updateUserProfile(profile: UserProfile) {
        userProfileDao.updateUserProfile(profile)
    }
    
    /**
     * Removes the user profile from the database.
     */
    override suspend fun deleteUserProfile() {
        userProfileDao.deleteUserProfile()
    }

    /**
     * Clears the user profile from the database.
     */
    override suspend fun clearUserProfile() {
        userProfileDao.deleteUserProfile()
    }

    /**
     * Retrieves the device ID from shared preferences or generates a new one if not found.
     */
    override fun getPersistentDeviceId(): String {
        val key = "device_id"
        return sharedPreferences.getString(key, null) ?: run {
            val newId = UUID.randomUUID().toString()
            sharedPreferences.edit { putString(key, newId) }
            newId
        }
    }
}
