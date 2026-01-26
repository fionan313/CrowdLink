package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * GetMessagesUseCase
 *
 * Retrieves a stream of messages exchanged with a specific friend.
 */
class GetMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(friendId: String): Flow<List<Message>> {
        return repository.getMessagesWithFriend(friendId)
    }
}
