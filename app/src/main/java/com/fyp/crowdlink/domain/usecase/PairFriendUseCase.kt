package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import javax.inject.Inject

class PairFriendUseCase @Inject constructor(
    private val friendRepository: FriendRepository
) {
    suspend operator fun invoke(deviceId: String, displayName: String) {
        val friend = Friend(
            deviceId = deviceId,
            displayName = displayName,
            publicKey = null  // Will add in Week 8
        )
        friendRepository.addFriend(friend)
    }
}