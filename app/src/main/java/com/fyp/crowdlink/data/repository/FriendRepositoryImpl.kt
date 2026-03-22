package com.fyp.crowdlink.data.repository

import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FriendRepositoryImpl
 *
 * This class implements the [FriendRepository] interface and serves as the single source of truth
 * for friend-related data. It delegates data operations to the [FriendDao].
 */
class FriendRepositoryImpl @Inject constructor(
    private val friendDao: FriendDao
) : FriendRepository {
    
    /**
     * Retrieves all friends as a Flow, which emits updates whenever the database changes.
     */
    override fun getAllFriends(): Flow<List<Friend>> {
        return friendDao.getAllFriends()
    }
    
    /**
     * Retrieves a specific friend by their unique device ID.
     */
    override suspend fun getFriendById(deviceId: String): Friend? {
        return friendDao.getFriendById(deviceId)
    }
    
    /**
     * Adds a new friend or updates an existing one in the database.
     */
    override suspend fun addFriend(friend: Friend) {
        friendDao.insertFriend(friend)
    }
    
    /**
     * Removes a friend from the database.
     */
    override suspend fun removeFriend(friend: Friend) {
        friendDao.deleteFriend(friend)
    }
    
    /**
     * Removes a friend from the database using their device ID.
     */
    override suspend fun removeFriendById(deviceId: String) {
        friendDao.deleteFriendById(deviceId)
    }

    override suspend fun unpairAllFriends() {
        friendDao.deleteAll()
    }
    
    /**
     * Checks if a friend with the given device ID exists in the database.
     */
    override suspend fun isFriendPaired(deviceId: String): Boolean {
        return friendDao.isFriendPaired(deviceId) > 0
    }
    
    /**
     * Updates the 'last seen' timestamp for a specific friend.
     */
    override suspend fun updateLastSeen(deviceId: String, timestamp: Long) {
        friendDao.updateLastSeen(deviceId, timestamp)
    }
}
