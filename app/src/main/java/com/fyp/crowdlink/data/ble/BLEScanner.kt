package com.fyp.crowdlink.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.usecase.EstimateDistanceUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val estimateDistanceUseCase: EstimateDistanceUseCase
) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    companion object {
        // Custom UUID for CrowdLink service
        val CROWDLINK_SERVICE_UUID: UUID = BleAdvertiser.SERVICE_UUID

        private const val TAG = "BleScanner"

        /**
         * Calculates smoothed RSSI using moving average.
         * Exposed for unit testing.
         */
        @VisibleForTesting
        fun calculateSmoothedRssi(rssiHistory: List<Int>): Int {
            return if (rssiHistory.isEmpty()) {
                0
            } else {
                rssiHistory.average().toInt()
            }
        }
    }

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Ring buffer for RSSI smoothing (last 10 readings per device)
    private val rssiHistory = mutableMapOf<String, ArrayDeque<Int>>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        // Filter for CrowdLink devices only
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(CROWDLINK_SERVICE_UUID))
            .build()

        try {
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            Log.d(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting scan: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            
            // Extract device ID from manufacturer data
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(BleAdvertiser.MANUFACTURER_ID)
            
            // Use the advertised ID if available, otherwise fallback to MAC address
            val deviceId = manufacturerData?.let { bytes ->
                String(bytes, Charsets.UTF_8)
            } ?: device.address

            // Update RSSI history for smoothing
            // We use the stable deviceId (our custom one) for the map key now
            val history = rssiHistory.getOrPut(deviceId) { ArrayDeque(10) }

            if (history.size >= 10) history.removeFirst()
            history.addLast(rssi)

            // Calculate smoothed RSSI (moving average)
            val smoothedRssi = history.average().toInt()

            // Estimate distance using use case
            val distance = estimateDistanceUseCase(smoothedRssi)

            val discoveredDevice = DiscoveredDevice(
                deviceId = deviceId,
                rssi = smoothedRssi,
                estimatedDistance = distance
            )

            // Update state flow
            val currentDevices = _discoveredDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.deviceId == deviceId }

            if (existingIndex != -1) {
                currentDevices[existingIndex] = discoveredDevice
            } else {
                currentDevices.add(discoveredDevice)
            }

            _discoveredDevices.value = currentDevices

            Log.d(TAG, "Device found: $deviceId, RSSI: $smoothedRssi, Distance: ${distance.toInt()}m")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            rssiHistory.clear()
            _discoveredDevices.value = emptyList()
            Log.d(TAG, "BLE scanning stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping scan: ${e.message}")
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}