package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
    
    @Update
    suspend fun updateUserProfile(profile: UserProfile)
    
    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()
}