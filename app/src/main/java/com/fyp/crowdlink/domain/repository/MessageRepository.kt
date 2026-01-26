package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * MessageRepository
 *
 * Interface for managing P2P messages.
 */
interface MessageRepository {
    fun getMessagesWithFriend(friendId: String): Flow<List<Message>>
    suspend fun sendMessage(message: Message): Long
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)
}
