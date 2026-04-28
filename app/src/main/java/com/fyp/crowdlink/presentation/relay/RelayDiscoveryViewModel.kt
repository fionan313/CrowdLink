package com.fyp.crowdlink.presentation.relay

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.RelayNodeConnection
import com.fyp.crowdlink.data.ble.RelayNodeScanner
import com.fyp.crowdlink.domain.model.RelayNode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.edit

/**
 * RelayDiscoveryViewModel
 *
 * Manages relay node scanning and connection state for the relay discovery screen.
 * When auto-connect is enabled, automatically connects to the first (strongest) relay
 * node as soon as one is discovered and no connection is currently active. The
 * auto-connect preference is persisted to SharedPreferences so it survives app restarts.
 */
@HiltViewModel
class RelayDiscoveryViewModel @Inject constructor(
    private val relayNodeScanner: RelayNodeScanner,
    private val relayNodeConnection: RelayNodeConnection,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("crowdlink_prefs", Context.MODE_PRIVATE)

    private val _autoConnect = MutableStateFlow(
        sharedPreferences.getBoolean("auto_connect_relay", true)
    )
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    val discoveredRelays: StateFlow<List<RelayNode>> = relayNodeScanner.discoveredRelays
    val isConnected: StateFlow<Boolean> = relayNodeConnection.isConnected

    init {
        // observe relay list, auto-connect preference and connection state together
        // so auto-connect fires as soon as all three conditions are met
        viewModelScope.launch {
            combine(discoveredRelays, autoConnect, isConnected) { relays, auto, connected ->
                Triple(relays, auto, connected)
            }.collect { (relays, auto, connected) ->
                if (auto && !connected && relays.isNotEmpty()) {
                    // relays are ordered by signal strength - first entry is the strongest
                    relayNodeConnection.connect(relays.first().deviceId)
                }
            }
        }
    }

    fun connectToRelay(address: String) {
        viewModelScope.launch { relayNodeConnection.connect(address) }
    }

    fun disconnectFromRelay() {
        viewModelScope.launch { relayNodeConnection.disconnect() }
    }

    /**
     * Toggles auto-connect and persists the new value to SharedPreferences.
     */
    fun setAutoConnect(enabled: Boolean) {
        _autoConnect.value = enabled
        sharedPreferences.edit { putBoolean("auto_connect_relay", enabled) }
    }

    fun startScanning() { relayNodeScanner.startScanning() }

    fun stopScanning() { relayNodeScanner.stopScanning() }
}