package com.fyp.crowdlink.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.fyp.crowdlink.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayNodeConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
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

    private var relayObserverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        startRelayQueueObserver()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.tag(TAG).d("Connected to relay node")
                    _isConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.tag(TAG).d("Disconnected from relay node")
                    _isConnected.value = false
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(SERVICE_UUID))
                writeCharacteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                Timber.tag(TAG).d("Services discovered, ready to send messages")
            } else {
                Timber.tag(TAG).w("onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).d("Message sent to relay successfully")
            } else {
                Timber.tag(TAG).e("Failed to send message to relay, status: $status")
            }
        }
    }

    /**
     * Observes the relay queue and attempts to deliver messages via ESP32 
     * if a node is currently connected.
     */
    private fun startRelayQueueObserver() {
        relayObserverJob?.cancel()
        relayObserverJob = scope.launch {
            messageRepository.getRelayQueue().collect { queue ->
                if (_isConnected.value && queue.isNotEmpty()) {
                    Timber.tag(TAG)
                        .d("Relay queue update: ${queue.size} messages waiting, ESP32 connected")
                    queue.forEach { meshMessage ->
                        // Attempt delivery via ESP32 BLE fallback
                        val payload = "${meshMessage.recipientId}:${String(meshMessage.payload, Charsets.UTF_8)}"
                        val success = sendMessage(payload)
                        if (success) {
                            Timber.tag(TAG)
                                .d("Successfully delivered message ${meshMessage.messageId} via ESP32, removing from queue")
                            messageRepository.removeFromRelayQueue(meshMessage.messageId)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            Timber.tag(TAG).e("Device not found. Unable to connect.")
            return false
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Timber.tag(TAG).d("Attempting to connect to $deviceAddress")
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        val characteristic = writeCharacteristic ?: run {
            Timber.tag(TAG).e("Write characteristic is not initialized.")
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
