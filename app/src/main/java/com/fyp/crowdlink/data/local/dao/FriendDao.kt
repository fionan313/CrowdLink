package com.fyp.crowdlink.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    fun getAllFriends(): Flow<List<Friend>>

    // Added this method to fix the build error
    @Query("SELECT * FROM friends ORDER BY pairedAt DESC")
    suspend fun getPairedFriends(): List<Friend>
    
    @Query("SELECT * FROM friends WHERE deviceId = :deviceId")
    suspend fun getFriendById(deviceId: String): Friend?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)
    
    @Delete
    suspend fun deleteFriend(friend: Friend)
    
    @Query("SELECT COUNT(*) FROM friends WHERE deviceId = :deviceId")
    suspend fun isFriendPaired(deviceId: String): Int
}