package com.fyp.crowdlink.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        android.util.Log.wtf("BLE_SCANNER", "BleScanner CREATED!")
    }
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    // NOTE: bluetoothLeScanner can be null if Bluetooth is off when the app starts. 
    // We should fetch it dynamically or check it before scanning.
    private val bluetoothLeScanner: BluetoothLeScanner? 
        get() = bluetoothAdapter?.bluetoothLeScanner
    
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()
    
    private val deviceCache = mutableMapOf<String, MutableList<Int>>()
    private var isScanning = false
    
    fun startDiscovery() {
        if (isScanning) {
            Log.d("BLE_SCANNER", "Already scanning")
            return
        }
        
        if (bluetoothAdapter == null) {
            Log.e("BLE_SCANNER", "Bluetooth Adapter is NULL (Device does not support Bluetooth?)")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BLE_SCANNER", "Bluetooth is DISABLED. Cannot start scan.")
            return
        }
        
        if (bluetoothLeScanner == null) {
            Log.e("BLE_SCANNER", "Bluetooth LE Scanner is NULL (Bluetooth might be off or unavailable)")
            return
        }
        
        Log.d("BLE_SCANNER", "Starting BLE scan")
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleAdvertiser.SERVICE_UUID))
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            Log.d("BLE_SCANNER", "✓ Scan started successfully")
        } catch (e: SecurityException) {
            Log.e("BLE_SCANNER", "✗ Permission denied", e)
        }
    }
    
    fun stopDiscovery() {
        if (!isScanning) return
        
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            deviceCache.clear()
            _discoveredDevices.value = emptyList()
            Log.d("BLE_SCANNER", "Scan stopped")
        } catch (e: SecurityException) {
            Log.e("BLE_SCANNER", "Permission denied when stopping", e)
        }
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val rssi = it.rssi
                
                // Extract device ID from SERVICE DATA as UUID bytes
                val serviceData = it.scanRecord?.getServiceData(ParcelUuid(BleAdvertiser.SERVICE_UUID))
                val deviceId = serviceData?.let { bytes ->
                    if (bytes.size == 16) {
                        // Convert 16 bytes back to UUID string
                        try {
                            val buffer = ByteBuffer.wrap(bytes)
                            val mostSigBits = buffer.long
                            val leastSigBits = buffer.long
                            val uuid = UUID(mostSigBits, leastSigBits)
                            val uuidString = uuid.toString()
                            Log.d("BLE_SCANNER", "✓ Extracted device ID from bytes: $uuidString")
                            uuidString
                        } catch (e: Exception) {
                            Log.e("BLE_SCANNER", "Failed to parse UUID bytes", e)
                            null
                        }
                    } else {
                        Log.w("BLE_SCANNER", "Invalid service data size: ${bytes.size}, expected 16")
                        null
                    }
                }
                
                if (deviceId != null) {
                    Log.d("BLE_SCANNER", "Discovered: $deviceId, RSSI: $rssi")
                    // It's one of ours!
                    updateDeviceRssi(deviceId, rssi)
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error"
            }
            Log.e("BLE_SCANNER", "✗ Scan failed: $errorMsg (code: $errorCode)")
            isScanning = false
        }
    }
    
    private fun updateDeviceRssi(deviceId: String, rssi: Int) {
        val rssiHistory = deviceCache.getOrPut(deviceId) { mutableListOf() }
        rssiHistory.add(rssi)
        
        if (rssiHistory.size > 10) {
            rssiHistory.removeAt(0)
        }
        
        val smoothedRssi = rssiHistory.average().toInt()
        val distance = calculateDistance(smoothedRssi)
        
        Log.d("BLE_SCANNER", "Device: $deviceId, Smoothed RSSI: $smoothedRssi, Distance: ${String.format("%.1f", distance)}m")
        
        val currentDevices = _discoveredDevices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.deviceId == deviceId }
        
        val device = DiscoveredDevice(
            deviceId = deviceId,
            rssi = smoothedRssi,
            estimatedDistance = distance,
            lastSeen = System.currentTimeMillis()
        )
        
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = device
        } else {
            currentDevices.add(device)
        }
        
        _discoveredDevices.value = currentDevices
    }
    
    private fun calculateDistance(rssi: Int): Double {
        if (rssi == 0) return -1.0
        
        val txPower = -59
        val pathLossExponent = 2.5
        
        return Math.pow(10.0, (txPower - rssi) / (10.0 * pathLossExponent))
    }
}