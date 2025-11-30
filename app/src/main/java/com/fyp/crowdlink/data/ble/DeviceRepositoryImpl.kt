package com.fyp.crowdlink.data.ble

import android.util.Log
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
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
    
    init {
        // Combine BLE scan results with friends list
        combine(
            bleScanner.discoveredDevices,
            friendRepository.getAllFriends()
        ) { rawDevices, friends ->
            val pairedFriendIds = friends.map { it.deviceId }.toSet()
            
            Log.d("DEVICE_REPO", "Paired friends: $pairedFriendIds")
            Log.d("DEVICE_REPO", "Discovered devices: ${rawDevices.map { it.deviceId }}")
            
            // Only show devices that are paired friends
            val filteredDevices = rawDevices.filter { device ->
                val isPaired = device.deviceId in pairedFriendIds
                if (isPaired) {
                    Log.d("DEVICE_REPO", "✓ ${device.deviceId} is a paired friend")
                } else {
                    // Log.d("DEVICE_REPO", "✗ ${device.deviceId} is NOT paired") // Reducing log spam
                }
                isPaired
            }
            
            filteredDevices
        }
        .onEach { filteredDevices ->
            _discoveredDevices.value = filteredDevices
        }
        .launchIn(CoroutineScope(Dispatchers.Default))
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

    override suspend fun getPairedFriends(): List<Friend> = withContext(Dispatchers.IO) {
        friendRepository.getAllFriends().first()
    }
}