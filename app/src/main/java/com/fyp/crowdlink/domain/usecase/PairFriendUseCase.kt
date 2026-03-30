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
     */
    suspend operator fun invoke(deviceId: String, displayName: String) {
        // Create a new Friend object. sharedKey is initially null until exchanged.
        val friend = Friend(
            deviceId = deviceId,
            displayName = displayName,
            sharedKey = null  // Will add in Week 8
        )
        // Persist the new friend to the database via the repository
        friendRepository.addFriend(friend)
    }
}
