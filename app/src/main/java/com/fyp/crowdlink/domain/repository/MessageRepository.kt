package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * MessageRepository
 *
 * Defines the contract for managing chat messages and the store-and-forward mesh relay queue.
 * The two concerns are kept in the same interface as both deal with message lifecycle -
 * a chat message and its underlying [MeshMessage] packet share the same send path.
 */
interface MessageRepository {

    /**
     * Retrieves a live stream of all messages exchanged with a specific friend, ordered by timestamp.
     *
     * @param friendId The device ID of the friend whose conversation to retrieve.
     * @return A Flow emitting the ordered list of [Message] objects whenever the conversation changes.
     */
    fun getMessagesWithFriend(friendId: String): Flow<List<Message>>

    /**
     * Persists an incoming or outbound message to the local database.
     *
     * @param message The [Message] to store.
     * @return The generated row ID of the inserted record.
     */
    suspend fun sendMessage(message: Message): Long

    /**
     * Updates the delivery status of a specific message.
     *
     * @param messageId The primary key of the message to update.
     * @param status The new [MessageStatus] to apply.
     */
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    /**
     * Enqueues a mesh packet for store-and-forward delivery via the BLE layer.
     *
     * @param message The [MeshMessage] to add to the relay queue.
     */
    suspend fun addToRelayQueue(message: MeshMessage)

    /**
     * Returns a live stream of all active (non-expired) packets in the relay queue.
     * Observed by BLEScanner, which drains it as peer connections become available.
     *
     * @return A Flow emitting the current list of [MeshMessage] objects awaiting relay.
     */
    fun getRelayQueue(): Flow<List<MeshMessage>>

    /**
     * Removes a successfully forwarded packet from the relay queue.
     *
     * @param messageId The UUID of the packet to remove.
     */
    suspend fun removeFromRelayQueue(messageId: UUID)

    /**
     * Purges all packets whose expiry time has elapsed.
     * Called periodically to prevent the relay queue from growing unbounded.
     */
    suspend fun purgeExpiredRelayMessages()

    /**
     * Deletes all local message history.
     * Called on a full reset or when clearing data for a specific friend.
     */
    suspend fun clearAllMessages()

    /**
     * Tracks the device ID of the friend whose chat is currently open.
     * Used to suppress incoming message notifications for the active conversation.
     */
    val activeChatFriendId: StateFlow<String?>

    /**
     * Sets the currently active chat friend, or null when no chat is open.
     *
     * @param friendId The device ID of the friend whose chat is open, or null.
     */
    fun setActiveChatFriend(friendId: String?)
}