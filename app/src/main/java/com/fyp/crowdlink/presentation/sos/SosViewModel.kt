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

@HiltViewModel
class SosViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val friendRepository: FriendRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _sosSent = MutableStateFlow(false)
    val sosSent: StateFlow<Boolean> = _sosSent.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    // --- Added for SosAlertScreen (Receiver Side) ---
    
    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend.asStateFlow()

    val myLocation = locationRepository.getMyLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadFriend(friendId: String) {
        viewModelScope.launch {
            _friend.value = friendRepository.getFriendById(friendId)
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth's radius in meters
        val phi1 = lat1 * PI / 180
        val phi2 = lat2 * PI / 180
        val deltaPhi = (lat2 - lat1) * PI / 180
        val deltaLambda = (lon2 - lon1) * PI / 180

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }
    // ------------------------------------------------

    fun sendSos() {
        viewModelScope.launch {
            _isSending.value = true
            deviceRepository.sendSosAlert()
            _sosSent.value = true
            _isSending.value = false
        }
    }
}
