package com.fyp.crowdlink.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FriendsViewModel
 *
 * Manages the friends list and handles unpairing. Unpairing is a two-step operation -
 * a BLE notification is sent to the remote device first so it can remove this device
 * from its own database, then the local record is deleted from Room.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    // live stream of all paired friends, drives the list in FriendsScreen
    val friends: StateFlow<List<Friend>> =
        friendRepository.getAllFriends()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Unpairs a friend by notifying their device over BLE then removing the local record.
     * The BLE notification allows the remote device to clean up its own database even
     * if the user is not present to initiate the removal themselves.
     */
    fun unpairFriend(friend: Friend) {
        viewModelScope.launch {
            deviceRepository.sendUnpairNotification(friend.deviceId)
            friendRepository.removeFriend(friend)
        }
    }
}