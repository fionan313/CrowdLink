package com.fyp.crowdlink.data.ble

import android.util.Log
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.NearbyFriend
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
    private val friendRepository: FriendRepository
) : DeviceRepository {

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // NEW: Combined nearby friends with distance
    private val _nearbyFriends = MutableStateFlow<List<NearbyFriend>>(emptyList())
    val nearbyFriends: StateFlow<List<NearbyFriend>> = _nearbyFriends.asStateFlow()

    init {
        // Combine BLE scan results with friends list
        combine(
            bleScanner.discoveredDevices,
            friendRepository.getAllFriends()
        ) { rawDevices, friends ->
            Log.d("DEVICE_REPO", "Raw devices: ${rawDevices.size}, Friends: ${friends.size}")

            // Create map of deviceId -> Friend for quick lookup
            val friendsMap = friends.associateBy { it.deviceId }

            // Match discovered devices with paired friends
            val nearbyFriends = rawDevices.mapNotNull { device ->
                val friend = friendsMap[device.deviceId]
                if (friend != null) {
                    Log.d("DEVICE_REPO", "✓ Nearby: ${friend.displayName} at ${String.format("%.1f", device.estimatedDistance)}m")
                    NearbyFriend(
                        deviceId = device.deviceId,
                        displayName = friend.displayName,
                        rssi = device.rssi,
                        estimatedDistance = device.estimatedDistance,
                        lastSeen = device.lastSeen
                    )
                } else {
                    Log.d("DEVICE_REPO", "✗ Device ${device.deviceId} not in friends list")
                    null
                }
            }

            nearbyFriends
        }.onEach { nearbyFriends ->
            _nearbyFriends.value = nearbyFriends
        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    override fun startDiscovery() {
        bleScanner.startDiscovery()
    }

    override fun stopDiscovery() {
        bleScanner.stopDiscovery()
    }

    override fun startAdvertising(myDeviceId: String) {
        bleAdvertiser.startAdvertising(myDeviceId)
    }

    override fun stopAdvertising() {
        bleAdvertiser.stopAdvertising()
    }

    override suspend fun getPairedFriends(): List<Friend> {
        TODO("Not yet implemented")
    }
}
