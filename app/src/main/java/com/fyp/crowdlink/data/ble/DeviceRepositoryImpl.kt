package com.fyp.crowdlink.data.ble

import android.Manifest
import androidx.annotation.RequiresPermission
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,  // ← ADD THIS
    private val friendDao: FriendDao
) : DeviceRepository {

    override val discoveredDevices: StateFlow<List<DiscoveredDevice>>
        get() = bleScanner.discoveredDevices

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun startDiscovery() {
        bleScanner.startScanning()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopDiscovery() {
        bleScanner.stopScanning()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising() {
        bleAdvertiser.startAdvertising()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        bleAdvertiser.stopAdvertising()
    }

    override suspend fun getPairedFriends(): List<Friend> = withContext(Dispatchers.IO) {
        friendDao.getPairedFriends()
    }
}