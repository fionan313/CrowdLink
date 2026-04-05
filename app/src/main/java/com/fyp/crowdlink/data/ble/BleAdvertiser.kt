package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid

import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.PairingRequest
import com.fyp.crowdlink.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {
    init {
        Timber.tag("BLE_ADVERTISER").wtf("BleAdvertiser CREATED!")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var isAdvertising = false
    private val _isGattServerReady = MutableStateFlow(false)
    val isGattServerReady: StateFlow<Boolean> = _isGattServerReady.asStateFlow()

    private var gattServer: BluetoothGattServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var onPairingRequestReceived: ((PairingRequest) -> Unit)? = null
    var onPairingAcceptedReceived: ((String) -> Unit)? = null
    var onUnpairRequestReceived: ((String) -> Unit)? = null
    var onSosAlertReceived: ((deviceAddress: String, rawPayload: ByteArray) -> Unit)? = null

    // GATT server callback handles incoming mesh packets
    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT connection state changed: device=${device.address} newState=$newState")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT service added status=$status uuid=${service?.uuid} at=${System.currentTimeMillis()}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _isGattServerReady.value = true
                startLeAdvertising()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == MESH_CHARACTERISTIC_UUID) {
                if (value.isNotEmpty()) {
                    when (value[0]) {
                        PAIRING_REQUEST_PREFIX -> handlePairingRequest(value)
                        PAIRING_ACCEPTED_PREFIX -> handlePairingAccepted(value)
                        UNPAIR_REQUEST_PREFIX -> handleUnpairRequest(value)
                        SOS_ALERT_PREFIX -> handleSosAlert(device, value)
                        ENCRYPTED_PAYLOAD_PREFIX -> {
                            onSosAlertReceived?.invoke(device.address, value)
                            Timber.tag("BLE_ADVERTISER").d("Encrypted direct payload from ${device.address}")
                        }
                        else -> handleMeshMessage(value)
                    }
                }

                // Acknowledge if required
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }
        }
    }

    private fun handlePairingRequest(value: ByteArray) {
        Timber.tag("BLE_ADVERTISER").d("handlePairingRequest fired at=${System.currentTimeMillis()}")
        try {
            // Skip prefix byte
            val jsonString = value.decodeToString(startIndex = 1)
            val json = JSONObject(jsonString)
            val sharedKey = if (json.has("sharedKey") && json.getString("sharedKey").isNotEmpty()) {
                json.getString("sharedKey")
            } else null
            val request = PairingRequest(
                senderDeviceId = json.getString("senderId"),
                senderDisplayName = json.getString("senderName"),
                sharedKey = sharedKey
            )
            onPairingRequestReceived?.invoke(request)
            Timber.tag("BLE_ADVERTISER")
                .d("Pairing request received from ${request.senderDisplayName}")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing request")
        }
    }

    private fun handlePairingAccepted(value: ByteArray) {
        try {
            val jsonString = value.decodeToString(startIndex = 1)
            val json = JSONObject(jsonString)
            val senderId = json.getString("senderId")
            onPairingAcceptedReceived?.invoke(senderId)
            Timber.tag("BLE_ADVERTISER").d("Pairing accepted by $senderId")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing acceptance")
        }
    }

    private fun handleUnpairRequest(value: ByteArray) {
        try {
            val json = JSONObject(value.decodeToString(startIndex = 1))
            val senderId = json.getString("senderId")
            onUnpairRequestReceived?.invoke(senderId)
            Timber.tag("BLE_ADVERTISER").d("Unpair request received from $senderId")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse unpair request")
        }
    }

    private fun handleSosAlert(device: BluetoothDevice, value: ByteArray) {
        onSosAlertReceived?.invoke(device.address, value)
        Timber.tag("BLE_ADVERTISER").d("SOS alert received (raw) from ${device.address}")
    }

    private fun handleMeshMessage(value: ByteArray) {
        val message = serializer.deserialize(value)
        if (message != null) {
            scope.launch {
                // Explicitly report this as coming over BLE
                meshRoutingEngine.processIncoming(message, TransportType.BLE)
            }
            Timber.tag("BLE_ADVERTISER").d("Handed to routing engine: ${message.messageId}")
        } else {
            Timber.tag("BLE_ADVERTISER").w("Failed to deserialise incoming mesh packet")
        }
    }

    // builds the GATT service with both discovery and mesh characteristics
    private fun buildGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Existing discovery characteristic — read only
        val discoveryCharacteristic = BluetoothGattCharacteristic(
            DISCOVERY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

         // mesh relay characteristic — writable by remote devices
        val meshCharacteristic = BluetoothGattCharacteristic(
            MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(discoveryCharacteristic)
        service.addCharacteristic(meshCharacteristic)

        return service
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(myDeviceId: String) {
        Timber.tag("BLE_ADVERTISER").d("Starting advertising with device ID: $myDeviceId")

        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Timber.tag("BLE_ADVERTISER").e("Bluetooth LE Advertiser not available")
            return
        }

        if (isAdvertising) {
            Timber.tag("BLE_ADVERTISER").d("Already advertising, stopping first")
            stopAdvertising()
        }

        // Store device ID for startLeAdvertising
        this.lastMyDeviceId = myDeviceId

        // start GATT server before advertising so it's ready when peers connect
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        Timber.tag("BLE_ADVERTISER").d("Adding GATT service at=${System.currentTimeMillis()}")
        gattServer?.addService(buildGattService())
        Timber.tag("BLE_ADVERTISER").d("GATT server started")
    }

    private var lastMyDeviceId: String? = null

    @SuppressLint("MissingPermission")
    private fun startLeAdvertising() {
        val myDeviceId = lastMyDeviceId ?: return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        // setConnectable(true) so peers can connect for mesh relay
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val deviceIdBytes = try {
            val uuid = UUID.fromString(myDeviceId)
            val buffer = ByteBuffer.allocate(16)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            buffer.array()
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Invalid UUID format: $myDeviceId")
            myDeviceId.take(16).toByteArray(Charsets.UTF_8)
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), deviceIdBytes)
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Timber.tag("BLE_ADVERTISER").e(e, "Permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()      // close GATT server alongside advertising
            gattServer = null
            isAdvertising = false
            _isGattServerReady.value = false
            Timber.tag("BLE_ADVERTISER").d("Advertising and GATT server stopped")
        } catch (e: SecurityException) {
            Timber.tag("BLE_ADVERTISER").e(e, "Permission denied when stopping")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Timber.tag("BLE_ADVERTISER").d("✓ Advertising started successfully")
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
            Timber.tag("BLE_ADVERTISER").e("✗ Advertising failed: $errorMsg (code: $errorCode)")
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
        val DISCOVERY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000FE9E-0000-1000-8000-00805f9b34fb")
        val MESH_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("a8f2e3d1-4b5c-6e7f-8a9b-0c1d2e3f4a5b")
        
        const val PAIRING_REQUEST_PREFIX: Byte = 0x01
        const val PAIRING_ACCEPTED_PREFIX: Byte = 0x02
        const val UNPAIR_REQUEST_PREFIX: Byte = 0x04
        const val SOS_ALERT_PREFIX: Byte = 0x05
        const val ENCRYPTED_PAYLOAD_PREFIX: Byte = 0xFF.toByte()
    }
}
