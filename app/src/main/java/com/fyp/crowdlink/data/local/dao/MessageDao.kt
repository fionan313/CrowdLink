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
 * Data Access Object (DAO) for the [Message] entity.
 * This interface defines the database interactions for managing peer-to-peer messages
 * exchanged over the BLE mesh. Messages are persisted locally in Room so that
 * conversation history is available regardless of whether the sender is currently in range.
 */
@Dao
interface MessageDao {

    /**
     * Retrieves the full conversation history with a specific friend, ordered by send time.
     * Matches on both directions — messages sent to and received from that friend.
     * Emits a new list whenever the conversation changes, driving the chat UI reactively.
     *
     * @param friendId The device ID of the friend whose conversation to retrieve.
     * @return A Flow emitting the ordered list of [Message] objects for that conversation.
     */
    @Query("SELECT * FROM messages WHERE senderId = :friendId OR receiverId = :friendId ORDER BY timestamp ASC")
    fun getMessagesWithFriend(friendId: String): Flow<List<Message>>

    /**
     * Inserts a new message into the database, or replaces it if a message with the
     * same primary key already exists. Replacement handles the case where a relayed
     * message arrives more than once across different hops.
     *
     * @param message The [Message] to persist.
     * @return The row ID of the inserted or replaced record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    /**
     * Updates the delivery status of a specific message.
     * Used to reflect changes such as a message moving from "sent" to "delivered"
     * once an acknowledgement is received over the mesh.
     *
     * @param messageId The primary key of the message to update.
     * @param status The new delivery status string (e.g. "SENT", "DELIVERED").
     */
    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    /**
     * Deletes all messages exchanged with a specific friend.
     * Called when a friend is unpaired, ensuring their conversation history
     * is removed alongside their friend record.
     *
     * @param friendId The device ID of the friend whose messages should be deleted.
     */
    @Query("DELETE FROM messages WHERE senderId = :friendId OR receiverId = :friendId")
    suspend fun deleteMessagesWithFriend(friendId: String)

    /**
     * Deletes all messages from the database.
     * Called on a full reset or when the user clears all app data.
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}