package com.fyp.crowdlink.data.repository

import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesWithFriend(friendId: String): Flow<List<Message>> {
        return messageDao.getMessagesWithFriend(friendId)
    }

    override suspend fun sendMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }

    override suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status.name)
    }
}
