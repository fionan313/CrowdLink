package com.fyp.crowdlink.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class FriendMapPin(
    val friend: Friend,
    val location: DeviceLocation
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _myLocation = MutableStateFlow<DeviceLocation?>(null)
    val myLocation: StateFlow<DeviceLocation?> = _myLocation.asStateFlow()

    private val _friendPins = MutableStateFlow<List<FriendMapPin>>(emptyList())
    val friendPins: StateFlow<List<FriendMapPin>> = _friendPins.asStateFlow()

    private val _selectedFriendId = MutableStateFlow<String?>(null)
    val selectedFriendId: StateFlow<String?> = _selectedFriendId.asStateFlow()

    private val _isCachingTiles = MutableStateFlow(false)
    val isCachingTiles: StateFlow<Boolean> = _isCachingTiles.asStateFlow()

    init {
        // Collect own GPS location
        locationRepository.getMyLocation()
            .onEach { _myLocation.value = it }
            .launchIn(viewModelScope)

        // Collect friends and their cached locations, combine into pins
        friendRepository.getAllFriends()
            .onEach { friends ->
                friends.forEach { friend ->
                    locationRepository.getFriendLocation(friend.deviceId)
                        .onEach { location ->
                            if (location != null) {
                                val existing = _friendPins.value.toMutableList()
                                val index = existing.indexOfFirst { it.friend.deviceId == friend.deviceId }
                                val pin = FriendMapPin(friend, location)
                                if (index >= 0) existing[index] = pin else existing.add(pin)
                                _friendPins.value = existing
                            }
                        }
                        .launchIn(viewModelScope)
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectFriend(friendId: String?) {
        _selectedFriendId.value = friendId
    }

    fun startTileCaching() {
        _isCachingTiles.value = true
        // Tile caching is handled in MapScreen via the MapLibre offline manager
        // This flag drives the UI indicator
    }

    fun onTileCachingComplete() {
        _isCachingTiles.value = false
    }
}
