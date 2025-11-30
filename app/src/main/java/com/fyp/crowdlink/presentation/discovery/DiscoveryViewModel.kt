package com.fyp.crowdlink.presentation.discovery

import android.Manifest
import android.content.SharedPreferences
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepositoryImpl,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> =
        deviceRepository.discoveredDevices

    // Generate or retrieve persistent device ID
    // MUST match the key used in PairingViewModel ("device_id")
    private val myDeviceId: String by lazy {
        sharedPreferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { newId ->
                sharedPreferences.edit {
                    putString(KEY_DEVICE_ID, newId)
                }
            }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        deviceRepository.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopDiscovery() {
        deviceRepository.stopDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising() {
        deviceRepository.startAdvertising(myDeviceId)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        deviceRepository.stopAdvertising()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public override fun onCleared() {
        super.onCleared()
        // It's safer to wrap these in try-catch or check permissions if possible, 
        // but standard ViewModel cleanup usually assumes permissions were granted if usage occurred.
        // However, stopDiscovery/Advertising might throw SecurityException if permission revoked.
        try {
            stopDiscovery()
            stopAdvertising()
        } catch (e: SecurityException) {
            // Permission revoked or not granted, just ignore
        }
    }

    companion object {
        // This key must match the one in PairingViewModel
        private const val KEY_DEVICE_ID = "device_id"
    }
}