
package com.fyp.crowdlink.presentation.discovery

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import android.content.SharedPreferences
import java.util.UUID
import androidx.core.content.edit

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepositoryImpl,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> =
        deviceRepository.discoveredDevices

    // Generate or retrieve persistent device ID
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
        stopDiscovery()
        stopAdvertising()
    }

    companion object {
        private const val KEY_DEVICE_ID = "my_device_id"
    }
}