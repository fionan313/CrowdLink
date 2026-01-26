package com.fyp.crowdlink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fyp.crowdlink.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * MessageDao
 *
 * Data Access Object for peer-to-peer messages.
 */
@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE senderId = :friendId OR receiverId = :friendId ORDER BY timestamp ASC")
    fun getMessagesWithFriend(friendId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    @Query("DELETE FROM messages WHERE senderId = :friendId OR receiverId = :friendId")
    suspend fun deleteMessagesWithFriend(friendId: String)
}
