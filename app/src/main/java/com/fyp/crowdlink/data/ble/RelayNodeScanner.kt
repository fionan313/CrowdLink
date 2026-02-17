package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
        const val RELAY_SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
        private const val TAG = "RelayNodeScanner"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredRelays = MutableStateFlow<List<RelayNode>>(emptyList())
    val discoveredRelays: StateFlow<List<RelayNode>> = _discoveredRelays.asStateFlow()

    private val relayMap = mutableMapOf<String, RelayNode>()

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return

            if (deviceName.startsWith("CrowdLink-Relay")) {
                val relay = RelayNode(
                    deviceId = device.address,
                    name = deviceName,
                    rssi = result.rssi,
                    isConnected = false
                )

                relayMap[device.address] = relay
                _discoveredRelays.value = relayMap.values.toList()

                Log.d(TAG, "Found relay: $deviceName at ${result.rssi} dBm")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(RELAY_SERVICE_UUID)))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d(TAG, "Started scanning for relay nodes")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped scanning for relay nodes")
    }
}