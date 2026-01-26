package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * SendMessageUseCase
 *
 * Handles the logic for sending a message to a friend.
 */
class SendMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(message: Message): Long {
        return repository.sendMessage(message)
    }
}
