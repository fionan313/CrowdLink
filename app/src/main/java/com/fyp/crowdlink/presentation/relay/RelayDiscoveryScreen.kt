package com.fyp.crowdlink.presentation.relay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.RelayNodeConnection
import com.fyp.crowdlink.data.ble.RelayNodeScanner
import com.fyp.crowdlink.domain.model.RelayNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RelayDiscoveryViewModel @Inject constructor(
    private val relayNodeScanner: RelayNodeScanner,
    private val relayNodeConnection: RelayNodeConnection
) : ViewModel() {

    val discoveredRelays: StateFlow<List<RelayNode>> = relayNodeScanner.discoveredRelays

    init {
        startScanning()
    }

    fun startScanning() {
        relayNodeScanner.startScanning()
    }

    fun connectToRelay(deviceAddress: String) {
        viewModelScope.launch {
            relayNodeConnection.connect(deviceAddress)
        }
    }

    override fun onCleared() {
        super.onCleared()
        relayNodeScanner.stopScanning()
        relayNodeConnection.disconnect()
    }
}