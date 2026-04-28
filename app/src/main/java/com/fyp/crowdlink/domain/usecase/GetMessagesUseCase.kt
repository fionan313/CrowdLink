package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * GetMessagesUseCase
 *
 * Retrieves a live stream of messages exchanged with a specific friend.
 * Delegates directly to [MessageRepository], keeping the ViewModel decoupled
 * from the data layer.
 */
class GetMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    /**
     * Invokes the use case for a specific conversation.
     *
     * @param friendId The device ID of the friend whose conversation to observe.
     * @return A Flow emitting the ordered list of [Message] objects whenever the conversation changes.
     */
    operator fun invoke(friendId: String): Flow<List<Message>> {
        return repository.getMessagesWithFriend(friendId)
    }
}