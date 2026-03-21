package com.fyp.crowdlink.presentation.discovery

import android.annotation.SuppressLint
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    val isMeshActive: StateFlow<Boolean> = combine(_isDiscovering, _isAdvertising) { scanning, advertising ->
        scanning && advertising
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Expose nearby friends with distance
    val nearbyFriends: StateFlow<List<NearbyFriend>> =
        deviceRepository.nearbyFriends

    // Relay node flows
    val discoveredRelays: StateFlow<List<RelayNode>> = relayNodeScanner.discoveredRelays
    val isRelayConnected: StateFlow<Boolean> = relayNodeConnection.isConnected

    private val _forceShowRelays = MutableStateFlow(sharedPreferences.getBoolean("force_show_relays", false))
    val forceShowRelays: StateFlow<Boolean> = _forceShowRelays.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "force_show_relays") {
            _forceShowRelays.value = prefs.getBoolean(key, false)
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        
        // Start discovery and advertising by default
        startDiscovery()
        startAdvertising()

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
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        // Do not stop discovery or advertising here.
        // BleScanner and BleAdvertiser are singletons that should remain
        // active while the app is in the foreground. Scanning is only
        // stopped explicitly when the user taps the MeshStatusPill to pause,
        // or when the app moves to the background via a foreground service.
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}
