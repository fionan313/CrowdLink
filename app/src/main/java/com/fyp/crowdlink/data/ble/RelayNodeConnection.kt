package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
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

/**
 * RelayNodeConnection
 *
 * Manages the BLE GATT connection to a nearby ESP32 relay node. When connected, it
 * observes the Room relay queue and forwards each pending packet to the node over
 * a writable GATT characteristic. Successfully forwarded messages are removed from
 * the queue immediately. The payload format is "recipientId:content" - simple enough
 * for the ESP32 firmware to parse without a full mesh header.
 */
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
                    gatt.discoverServices() // discover the write characteristic before sending
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
     * Observes the Room relay queue. When an ESP32 is connected and messages are waiting,
     * drains the queue by forwarding each one in order. Removes each message from the queue
     * only after a successful write to avoid double-delivery on reconnect.
     */
    private fun startRelayQueueObserver() {
        relayObserverJob?.cancel()
        relayObserverJob = scope.launch {
            messageRepository.getRelayQueue().collect { queue ->
                if (_isConnected.value && queue.isNotEmpty()) {
                    Timber.tag(TAG).d("Relay queue: ${queue.size} messages waiting, ESP32 connected")
                    queue.forEach { meshMessage ->
                        // "recipientId:payload" - compact enough for the ESP32 to parse
                        val payload = "${meshMessage.recipientId}:${String(meshMessage.payload, Charsets.UTF_8)}"
                        val success = sendMessage(payload)
                        if (success) {
                            Timber.tag(TAG).d("Delivered ${meshMessage.messageId} via ESP32, removing from queue")
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

    /**
     * Writes a string payload to the relay characteristic. Handles the Android 13 API split -
     * uses the new signature on TIRAMISU+ and falls back to the deprecated approach below.
     */
    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        val characteristic = writeCharacteristic ?: run {
            Timber.tag(TAG).e("Write characteristic is not initialized.")
            return false
        }

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                message.toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = message.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    // close the GATT connection and reset all state
    @SuppressLint("MissingPermission")
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        _isConnected.value = false
    }
}