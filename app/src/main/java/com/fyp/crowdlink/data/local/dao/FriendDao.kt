package com.fyp.crowdlink.data.local.dao

import androidx.room.*
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE is_paired = 1")
    suspend fun getPairedFriends(): List<Friend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("DELETE FROM friends")
    suspend fun deleteAll()
}