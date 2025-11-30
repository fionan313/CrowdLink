package com.fyp.crowdlink.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var isAdvertising = false
    
    fun startAdvertising(myDeviceId: String) {
        Log.d("BLE_ADVERTISER", "Starting advertising with device ID: $myDeviceId")
        
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e("BLE_ADVERTISER", "Bluetooth LE Advertiser not available")
            return
        }
        
        if (isAdvertising) {
            Log.d("BLE_ADVERTISER", "Already advertising, stopping first")
            stopAdvertising()
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)  // Advertise indefinitely
            .build()
        
        // Encode device ID as service data (supports longer strings)
        val deviceIdBytes = myDeviceId.toByteArray(Charsets.UTF_8)
        
        Log.d("BLE_ADVERTISER", "Device ID bytes length: ${deviceIdBytes.size}")
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), deviceIdBytes)  // ← Use SERVICE DATA!
            .build()
        
        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("BLE_ADVERTISER", "Permission denied", e)
        }
    }
    
    fun stopAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d("BLE_ADVERTISER", "Advertising stopped")
        } catch (e: SecurityException) {
            Log.e("BLE_ADVERTISER", "Permission denied when stopping", e)
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.d("BLE_ADVERTISER", "✓ Advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error"
            }
            Log.e("BLE_ADVERTISER", "✗ Advertising failed: $errorMsg (code: $errorCode)")
        }
    }
    
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
    }
}