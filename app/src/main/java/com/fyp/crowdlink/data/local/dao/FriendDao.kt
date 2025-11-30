package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    
    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    suspend fun getPairedFriends(): List<Friend>
    
    @Query("SELECT * FROM friends WHERE deviceId = :deviceId")
    suspend fun getFriendById(deviceId: String): Friend?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)
    
    @Delete
    suspend fun deleteFriend(friend: Friend)  // ← For unpair
    
    @Query("DELETE FROM friends WHERE deviceId = :deviceId")
    suspend fun deleteFriendById(deviceId: String)  // ← Alternative unpair
    
    @Query("SELECT COUNT(*) FROM friends WHERE deviceId = :deviceId")
    suspend fun isFriendPaired(deviceId: String): Int
    
    @Query("UPDATE friends SET lastSeen = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)
}