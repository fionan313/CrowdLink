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
 * Interface for managing P2P messages.
 */
interface MessageRepository {
    fun getMessagesWithFriend(friendId: String): Flow<List<Message>>
    suspend fun sendMessage(message: Message): Long
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    suspend fun addToRelayQueue(message: MeshMessage)
    fun getRelayQueue(): Flow<List<MeshMessage>>
    suspend fun removeFromRelayQueue(messageId: UUID)
    suspend fun purgeExpiredRelayMessages()
    
    // Tracks the currently active chat friend ID to suppress notifications
    val activeChatFriendId: StateFlow<String?>
    fun setActiveChatFriend(friendId: String?)
}
