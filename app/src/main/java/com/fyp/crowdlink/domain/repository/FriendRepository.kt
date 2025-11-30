package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun getAllFriends(): Flow<List<Friend>>
    suspend fun getFriendById(deviceId: String): Friend?
    suspend fun addFriend(friend: Friend)
    suspend fun removeFriend(friend: Friend)  // ← Add this
    suspend fun removeFriendById(deviceId: String)  // ← Add this
    suspend fun isFriendPaired(deviceId: String): Boolean
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)
}