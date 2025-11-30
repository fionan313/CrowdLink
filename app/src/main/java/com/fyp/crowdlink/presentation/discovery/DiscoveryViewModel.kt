package com.fyp.crowdlink.presentation.discovery

import android.Manifest
import android.R.attr.id
import android.content.SharedPreferences
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.NearbyFriend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepositoryImpl,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    // Generate or retrieve persistent device ID
    private val myDeviceId: String by lazy {
        val id = sharedPreferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { newId ->
                sharedPreferences.edit { putString(KEY_DEVICE_ID, newId) }
            }
        android.util.Log.wtf("DISCOVERY_VM", "Device ID loaded: $id")
        id
    }

    init {
        android.util.Log.wtf("DISCOVERY_VM", "DiscoveryViewModel CREATED!")
        android.util.Log.wtf("DISCOVERY_VM", "My Device ID: $myDeviceId")
    }

    // NEW: Expose nearby friends with distance
    val nearbyFriends: StateFlow<List<NearbyFriend>> =
        deviceRepository.nearbyFriends



    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        android.util.Log.wtf("DISCOVERY_VM", "!!! START DISCOVERY CALLED !!!")
        deviceRepository.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopDiscovery() {
        deviceRepository.stopDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising() {
        android.util.Log.wtf("DISCOVERY_VM", "!!! START ADVERTISING CALLED !!!")
        android.util.Log.wtf("DISCOVERY_VM", "Advertising with device ID: $myDeviceId")
        deviceRepository.startAdvertising(myDeviceId)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        deviceRepository.stopAdvertising()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            stopDiscovery()
            stopAdvertising()
        } catch (e: SecurityException) {
            // Permission revoked, ignore
        }
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}