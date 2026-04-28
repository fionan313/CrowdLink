package com.fyp.crowdlink.presentation.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

/**
 * SosViewModel
 *
 * Shared between the SOS send path (triggered from [FriendsScreen]) and the SOS receive
 * path ([SosAlertScreen]). On the send side it delegates to [DeviceRepository.sendSosAlert]
 * and exposes [isSending] and [sosSent] for the button state in [FriendsScreen]. On the
 * receive side it loads the sender's [Friend] record and the local GPS fix so [SosAlertScreen]
 * can calculate and display an estimated distance to the sender.
 */
@HiltViewModel
class SosViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val friendRepository: FriendRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    // send-side state - drives the SOS button appearance in FriendsScreen
    private val _sosSent = MutableStateFlow(false)
    val sosSent: StateFlow<Boolean> = _sosSent.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    // receive-side state - used by SosAlertScreen to show sender details and distance
    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend.asStateFlow()

    // live GPS fix observed for distance calculation on the alert screen
    val myLocation = locationRepository.getMyLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Loads the sender's [Friend] record from Room so the alert screen can display
     * their last seen timestamp and other metadata.
     */
    fun loadFriend(friendId: String) {
        viewModelScope.launch {
            _friend.value = friendRepository.getFriendById(friendId)
        }
    }

    /**
     * Calculates the great-circle distance between two GPS coordinates using the
     * Haversine formula. Returns the result in metres.
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth's radius in metres
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    /**
     * Triggers an SOS alert broadcast to all discoverable paired friends.
     * Sets [isSending] while the operation is in progress and [sosSent] on completion
     * to update the button state in [FriendsScreen].
     */
    fun sendSos() {
        viewModelScope.launch {
            _isSending.value = true
            deviceRepository.sendSosAlert()
            _sosSent.value = true
            _isSending.value = false
        }
    }
}