package com.fyp.crowdlink.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.fyp.crowdlink.domain.model.RelayNode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// scans for ESP32 relay nodes advertising the CrowdLink relay service UUID.
// separate from BleScanner — that handles peer phones, this handles hardware nodes only
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

    // computed each time in case BT was toggled
    private val scanner get() = adapter?.bluetoothLeScanner
    private var activeScanner: BluetoothLeScanner? = null

    private val _discoveredRelays = MutableStateFlow<List<RelayNode>>(emptyList())
    val discoveredRelays: StateFlow<List<RelayNode>> = _discoveredRelays.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"

            // only surface devices broadcasting the CrowdLink relay name prefix
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

        // known Android lint false positive for BLE scan callbacks
        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.tag(TAG).e("Scan failed with error code: $errorCode")
        }
    }

    // upsert into the list, keep sorted strongest signal first
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
        val s = scanner
        if (s == null) {
            Timber.tag(TAG).e("BluetoothLeScanner is null — permissions granted?")
            return
        }
        activeScanner = s

        // filter by service UUID so we only see CrowdLink relay nodes, not every BLE device
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        s.startScan(filters, settings, scanCallback)
        Timber.tag(TAG).d("Started scanning for relays")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        activeScanner?.stopScan(scanCallback)
        activeScanner = null
        Timber.tag(TAG).d("Stopped scanning for relays")
    }
}