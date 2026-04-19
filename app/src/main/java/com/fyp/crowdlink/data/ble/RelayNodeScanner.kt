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

/**
 * RelayNodeScanner
 *
 * Scans specifically for ESP32 relay nodes advertising the CrowdLink relay service UUID.
 * Kept separate from [BleScanner] - that class handles peer phones, this handles fixed
 * hardware nodes only. Discovered relays are kept sorted by RSSI so the strongest node
 * is always first, ready for auto-connect in [RelayNodeConnection].
 */
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

    // fetched fresh each access in case BT was toggled off and on
    private val scanner get() = adapter?.bluetoothLeScanner
    private var activeScanner: BluetoothLeScanner? = null

    private val _discoveredRelays = MutableStateFlow<List<RelayNode>>(emptyList())
    val discoveredRelays: StateFlow<List<RelayNode>> = _discoveredRelays.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"

            // only surface devices whose name starts with the CrowdLink relay prefix
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

    /**
     * Upserts a relay into the discovered list. Existing entries are replaced by MAC address,
     * new entries are appended. The list is re-sorted by RSSI descending after every update
     * so the strongest node remains at index 0 for auto-connect.
     */
    private fun updateDiscoveredRelays(relay: RelayNode) {
        val currentList = _discoveredRelays.value.toMutableList()
        val index = currentList.indexOfFirst { it.deviceId == relay.deviceId }
        if (index != -1) currentList[index] = relay else currentList.add(relay)
        _discoveredRelays.value = currentList.sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val s = scanner
        if (s == null) {
            Timber.tag(TAG).e("BluetoothLeScanner is null - permissions granted?")
            return
        }
        activeScanner = s

        // filter by relay service UUID - ignores all phones and non-CrowdLink BLE devices
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