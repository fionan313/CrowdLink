package com.fyp.crowdlink.data.ble

import android.Manifest
import androidx.annotation.RequiresPermission
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        bleScanner.discoveredDevices.onEach { rawDevices ->
            // Filter to only paired friends
            val pairedFriendIds = friendRepository.getAllFriends()
                .first()  // Get current friends list
                .map { it.deviceId }
                .toSet()
            
            // Only show devices that are in friends list
            val filteredDevices = rawDevices.filter { device ->
                device.deviceId in pairedFriendIds
            }
            
            _discoveredDevices.value = filteredDevices
        }.launchIn(CoroutineScope(Dispatchers.Default))
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun startDiscovery() {
        bleScanner.startScanning()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopDiscovery() {
        bleScanner.stopScanning()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(myDeviceId: String) {
        bleAdvertiser.startAdvertising(myDeviceId)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        bleAdvertiser.stopAdvertising()
    }

    override suspend fun getPairedFriends(): List<Friend> = withContext(Dispatchers.IO) {
        // Since we switched to Repository, we might need to adjust this or remove it if not in interface.
        // The interface usually has it filtered. But if getPairedFriends is in Repository interface, we use it.
        // However, FriendRepository returns Flow.
        // If DeviceRepository interface demands getPairedFriends() as List, we might need to fetch it.
        // But the prompt for FriendRepositoryImpl implementation showed getAllFriends() returning Flow.
        // Let's assume we can just get the first emission of the flow for now or use isFriendPaired checks.
        // Actually, looking at previous turns, I added getPairedFriends() to FriendDao but not explicitly FriendRepository.
        // But wait, Step 1 prompt shows "private val friendRepository: FriendRepository" injection.
        // I will use the repository flow here.
        friendRepository.getAllFriends().first()
    }
}