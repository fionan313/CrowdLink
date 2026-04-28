package com.fyp.crowdlink.data.repository

import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.RelayMessageDao
import com.fyp.crowdlink.data.local.entity.toDomain
import com.fyp.crowdlink.data.local.entity.toEntity
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MessageRepositoryImpl
 *
 * Concrete implementation of [MessageRepository]. Manages two separate concerns:
 * chat message persistence via [MessageDao], and the store-and-forward mesh relay
 * queue via [RelayMessageDao]. Also tracks which chat is currently open so that
 * incoming messages for that friend can be marked as read immediately.
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val relayMessageDao: RelayMessageDao
) : MessageRepository {

    // tracks which chat is open so incoming messages can be marked read immediately
    private val _activeChatFriendId = MutableStateFlow<String?>(null)
    override val activeChatFriendId: StateFlow<String?> = _activeChatFriendId.asStateFlow()

    override fun setActiveChatFriend(friendId: String?) {
        _activeChatFriendId.value = friendId
    }

    override fun getMessagesWithFriend(friendId: String): Flow<List<Message>> {
        return messageDao.getMessagesWithFriend(friendId)
    }

    override suspend fun sendMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }

    override suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status.name)
    }

    /**
     * Enqueues an outbound mesh packet in Room. [BLEScanner] observes the relay queue
     * and drains it as peer connections become available.
     */
    override suspend fun addToRelayQueue(message: MeshMessage) {
        relayMessageDao.insert(message.toEntity())
    }

    override fun getRelayQueue(): Flow<List<MeshMessage>> {
        return relayMessageDao.getActiveRelayQueue().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun removeFromRelayQueue(messageId: UUID) {
        relayMessageDao.delete(messageId.toString())
    }

    override suspend fun purgeExpiredRelayMessages() {
        relayMessageDao.purgeExpired()
    }

    override suspend fun clearAllMessages() {
        messageDao.deleteAllMessages()
    }
}