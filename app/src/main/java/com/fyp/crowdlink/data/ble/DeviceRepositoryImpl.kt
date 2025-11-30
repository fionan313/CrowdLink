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

    // We listen to the scanner directly
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices

    // NEW: Combined nearby friends with distance
    private val _nearbyFriends = MutableStateFlow<List<NearbyFriend>>(emptyList())
    val nearbyFriends: StateFlow<List<NearbyFriend>> = _nearbyFriends.asStateFlow()

    init {
        // Combine BLE scan results with friends list
        combine(
            bleScanner.discoveredDevices,
            friendRepository.getAllFriends()
        ) { rawDevices, friends ->
            // android.util.Log.wtf("DEVICE_REPO", "=== COMBINE TRIGGERED ===")
            
            // Create map of SHORT ID -> Friend
            // We will match by checking if the scanned device ID (which might be short) 
            // matches the first 16 chars of the friend's full device ID.
            // OR if the scanned ID is the full ID.
            
            val friendsMap = friends.associateBy { it.deviceId.take(16) }
            
            val nearbyFriends = rawDevices.mapNotNull { device ->
                // Match using short ID (first 16 chars) or raw if UUID format
                val shortDeviceId = try {
                    // If it's a valid UUID, take first 16 chars of the UUID string? 
                    // No, our BleScanner now returns full UUID string if parsed from bytes.
                    // But BleAdvertiser sends 16 bytes of UUID.
                    // So BleScanner converts 16 bytes -> UUID object -> String.
                    // That String will be a full random UUID (with least sig bits?). 
                    // Wait, BleAdvertiser sends MOST SIG and LEAST SIG bits?
                    // No, my code sent 16 bytes. UUID is 128 bits = 16 bytes.
                    // So BleScanner reconstructs the FULL UUID.
                    // So we should compare FULL UUIDs if possible, or just take(16) of string if consistent.
                    
                    // If BleAdvertiser sends full 16 bytes of UUID, then Scanner gets full UUID.
                    // So we can compare full strings!
                    
                    device.deviceId
                } catch(e: Exception) {
                    device.deviceId
                }
                
                // However, we want to be robust. Let's try full match first.
                // If friends map is keyed by FULL ID:
                val friendFull = friends.find { it.deviceId == device.deviceId }
                
                if (friendFull != null) {
                     android.util.Log.wtf("DEVICE_REPO", "✓ MATCH FULL: ${friendFull.displayName}")
                     return@mapNotNull NearbyFriend(
                        deviceId = friendFull.deviceId,
                        displayName = friendFull.displayName,
                        rssi = device.rssi,
                        estimatedDistance = device.estimatedDistance,
                        lastSeen = device.lastSeen
                    )
                }
                
                // Fallback to short match if needed (e.g. if we change protocol later)
                null
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
        return friendRepository.getAllFriends().first()
    }
}