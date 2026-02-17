package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.fyp.crowdlink.domain.model.RelayNode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayNodeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RelayNodeScanner"
        const val SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private val _discoveredRelays = MutableStateFlow<List<RelayNode>>(emptyList())
    val discoveredRelays: StateFlow<List<RelayNode>> = _discoveredRelays.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"
            
            if (name.startsWith("CrowdLink-Relay")) {
                val relay = RelayNode(
                    deviceId = device.address,
                    name = name,
                    rssi = result.rssi,
                    lastSeen = System.currentTimeMillis()
                )
                updateDiscoveredRelays(relay)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

    private fun updateDiscoveredRelays(relay: RelayNode) {
        val currentList = _discoveredRelays.value.toMutableList()
        val index = currentList.indexOfFirst { it.deviceId == relay.deviceId }
        if (index != -1) {
            currentList[index] = relay
        } else {
            currentList.add(relay)
        }
        _discoveredRelays.value = currentList.sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        Log.d(TAG, "Started scanning for relays")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped scanning for relays")
    }
}
