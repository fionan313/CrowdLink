package com.fyp.crowdlink.presentation.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.data.ble.RelayNodeConnection
import com.fyp.crowdlink.data.ble.RelayNodeScanner
import com.fyp.crowdlink.domain.model.NearbyFriend
import com.fyp.crowdlink.domain.model.RelayNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepositoryImpl,
    private val relayNodeScanner: RelayNodeScanner,
    private val relayNodeConnection: RelayNodeConnection,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    // Generate or retrieve persistent device ID
    private val myDeviceId: String by lazy {
        val id = sharedPreferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { newId ->
                sharedPreferences.edit { putString(KEY_DEVICE_ID, newId) }
            }
        id
    }

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    // Expose nearby friends with distance
    val nearbyFriends: StateFlow<List<NearbyFriend>> =
        deviceRepository.nearbyFriends

    // Relay node flows
    val discoveredRelays: StateFlow<List<RelayNode>> = relayNodeScanner.discoveredRelays
    val isRelayConnected: StateFlow<Boolean> = relayNodeConnection.isConnected

    init {
        // Auto-connect to relay logic
        viewModelScope.launch {
            discoveredRelays.collect { relays ->
                val autoConnect = sharedPreferences.getBoolean("auto_connect_relay", true)
                if (autoConnect && !isRelayConnected.value && relays.isNotEmpty()) {
                    relayNodeConnection.connect(relays.first().deviceId)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        deviceRepository.startDiscovery()
        relayNodeScanner.startScanning()
        _isDiscovering.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        deviceRepository.stopDiscovery()
        relayNodeScanner.stopScanning()
        _isDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        deviceRepository.startAdvertising(myDeviceId)
        _isAdvertising.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        deviceRepository.stopAdvertising()
        _isAdvertising.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // We keep discovery running even when navigating away, 
        // but if the ViewModel is truly destroyed (app exit/back from start), we stop.
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
