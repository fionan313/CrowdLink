package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import javax.inject.Inject

/**
 * PairFriendUseCase
 *
 * This use case encapsulates the business logic for pairing with a new friend.
 * It takes the friend's device ID and display name, creates a new [Friend] object,
 * and persists it to the repository.
 */
class PairFriendUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    /**
     * Invokes the use case to pair a new friend.
     *
     * @param deviceId The unique identifier of the friend's device.
     * @param displayName The name to display for the friend.
     * @param sharedKey The AES-256-GCM symmetric key from pairing.
     */
    suspend operator fun invoke(deviceId: String, displayName: String, sharedKey: String? = null) {
        // Create a new Friend object.
        val friend = Friend(
            deviceId = deviceId,
            displayName = displayName,
            sharedKey = sharedKey
        )
        // Persist the new friend to the database via the repository
        friendRepository.addFriend(friend)
    }
}
