package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun getAllFriends(): Flow<List<Friend>>
    suspend fun addFriend(friend: Friend)
    suspend fun removeFriend(friend: Friend)
    suspend fun isFriendPaired(deviceId: String): Boolean
}