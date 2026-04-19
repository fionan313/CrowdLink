package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * SendMessageUseCase
 *
 * Persists an outbound message to the local database and hands it to the repository
 * for delivery over the BLE mesh. Keeping this logic in a use case ensures the
 * ViewModel remains unaware of the underlying transport and storage details.
 */
class SendMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    /**
     * Invokes the use case to send a message.
     *
     * @param message The [Message] to persist and enqueue for delivery.
     * @return The generated row ID of the inserted record.
     */
    suspend operator fun invoke(message: Message): Long {
        return repository.sendMessage(message)
    }
}