package com.fyp.crowdlink.presentation.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.data.ble.RelayNodeConnection
import com.fyp.crowdlink.data.ble.RelayNodeScanner
import com.fyp.crowdlink.domain.model.NearbyFriend
import com.fyp.crowdlink.domain.model.RelayNode
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.usecase.ShareLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * DiscoveryViewModel
 *
 * Manages the lifecycle of BLE scanning, advertising, relay node discovery and background
 * location broadcasting for the discovery screen. Mesh active state is derived by combining
 * the scanning and advertising flags - both must be true for the mesh to be considered active.
 * Hardware radio state is polled every 2 seconds to keep the connectivity banner up to date.
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepositoryImpl,
    private val relayNodeScanner: RelayNodeScanner,
    private val relayNodeConnection: RelayNodeConnection,
    private val sharedPreferences: SharedPreferences,
    private val shareLocationUseCase: ShareLocationUseCase,
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // polled every 2 seconds - drives the ConnectivityBanner in the UI
    val isBluetoothEnabled: StateFlow<Boolean> = flow {
        while (true) {
            emit(bluetoothAdapter?.isEnabled == true)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isWifiEnabled: StateFlow<Boolean> = flow {
        while (true) {
            emit(wifiManager.isWifiEnabled)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // lazily retrieve or generate a persistent device ID stored in SharedPreferences
    private val myDeviceId: String by lazy {
        sharedPreferences.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { newId ->
                sharedPreferences.edit { putString(KEY_DEVICE_ID, newId) }
            }
    }

    private val _isDiscovering = MutableStateFlow(false)
    private val _isAdvertising = MutableStateFlow(false)

    // mesh is only considered active when both scanning and advertising are running simultaneously
    val isMeshActive: StateFlow<Boolean> = combine(_isDiscovering, _isAdvertising) { scanning, advertising ->
        scanning && advertising
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val nearbyFriends: StateFlow<List<NearbyFriend>> = deviceRepository.nearbyFriends

    val discoveredRelays: StateFlow<List<RelayNode>> = relayNodeScanner.discoveredRelays
    val isRelayConnected: StateFlow<Boolean> = relayNodeConnection.isConnected

    // reflects the debug "force_show_relays" preference, updated reactively via the listener below
    private val _forceShowRelays = MutableStateFlow(
        sharedPreferences.getBoolean("force_show_relays", false)
    )
    val forceShowRelays: StateFlow<Boolean> = _forceShowRelays.asStateFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "force_show_relays") {
                _forceShowRelays.value = prefs.getBoolean(key, false)
            }
        }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        // auto-start scanning and advertising on launch
        startDiscovery()
        startAdvertising()

        // broadcast location to all paired friends every 60 seconds if location sharing is enabled
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val locationEnabled = sharedPreferences.getBoolean("location_sharing", true)
                if (locationEnabled) {
                    try {
                        val friends = friendRepository.getAllFriends().first()
                        friends.forEach { friend ->
                            shareLocationUseCase(friend.deviceId)
                        }
                        Timber.tag("DiscoveryViewModel")
                            .d("Background location broadcast sent to ${friends.size} friends")
                    } catch (e: Exception) {
                        Timber.tag("DiscoveryViewModel")
                            .e(e, "Background location broadcast failed")
                    }
                }
            }
        }

        // auto-connect to the first available relay node if the setting is enabled
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
    fun startAdvertising(myDeviceId: String) {
        deviceRepository.startAdvertising(myDeviceId)
        _isAdvertising.value = true
    }

    // convenience overload that uses the locally stored device ID
    fun startAdvertising() = startAdvertising(myDeviceId)

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        deviceRepository.stopAdvertising()
        _isAdvertising.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // unregister to prevent a SharedPreferences listener leak after the VM is destroyed
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}