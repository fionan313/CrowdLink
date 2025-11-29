package com.fyp.crowdlink.data.repository

import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val friendDao: FriendDao
) : FriendRepository {
    
    override fun getAllFriends(): Flow<List<Friend>> = 
        friendDao.getAllFriends()
    
    override suspend fun addFriend(friend: Friend) = 
        friendDao.insertFriend(friend)
    
    override suspend fun removeFriend(friend: Friend) = 
        friendDao.deleteFriend(friend)
    
    override suspend fun isFriendPaired(deviceId: String): Boolean =
        friendDao.isFriendPaired(deviceId) > 0
}