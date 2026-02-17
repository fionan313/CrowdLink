package com.fyp.crowdlink.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayNodeConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
        const val CHARACTERISTIC_UUID = "87654321-4321-4321-4321-cba987654321"
        private const val TAG = "RelayNodeConnection"
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to relay node")
                    _isConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from relay node")
                    _isConnected.value = false
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                writeCharacteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                Log.d(TAG, "Services discovered, ready to send messages")
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Message sent to relay successfully")
            } else {
                Log.e(TAG, "Failed to send message to relay, status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "Device not found. Unable to connect.")
            return false
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Attempting to connect to $deviceAddress")
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        val characteristic = writeCharacteristic ?: run {
            Log.e(TAG, "Write characteristic is not initialized.")
            return false
        }

        characteristic.value = message.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return bluetoothGatt?.writeCharacteristic(characteristic) ?: false
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        _isConnected.value = false
    }
}
