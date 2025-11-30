package com.fyp.crowdlink.data.repository

import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.model.UserProfile
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao
) : UserProfileRepository {
    
    override fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getUserProfile()
    }
    
    override suspend fun getUserProfileOnce(): UserProfile? {
        return userProfileDao.getUserProfileOnce()
    }
    
    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertUserProfile(profile)
    }
    
    override suspend fun updateUserProfile(profile: UserProfile) {
        userProfileDao.updateUserProfile(profile)
    }
    
    override suspend fun deleteUserProfile() {
        userProfileDao.deleteUserProfile()
    }
}