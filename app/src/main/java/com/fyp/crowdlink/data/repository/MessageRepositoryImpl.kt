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

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val relayMessageDao: RelayMessageDao
) : MessageRepository {

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
