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

/**
 * BleAdvertiser
 *
 * Server side of the BLE mesh. Broadcasts this device's presence via BLE advertising
 * and hosts a GATT server that receives all incoming writes from peers - pairing requests,
 * mesh messages, SOS alerts and unpair notifications. Advertising is deliberately gated
 * behind [onServiceAdded] to prevent peers from connecting before the characteristic exists.
 */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine, // decides what to do with incoming mesh packets
    private val serializer: MeshMessageSerialiser     // converts between bytes and MeshMessage objects
) {
    init {
        Timber.tag("BLE_ADVERTISER").wtf("BleAdvertiser CREATED!")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var isAdvertising = false

    // other components observe this before attempting GATT writes
    private val _isGattServerReady = MutableStateFlow(false)
    val isGattServerReady: StateFlow<Boolean> = _isGattServerReady.asStateFlow()

    private var gattServer: BluetoothGattServer? = null

    // background scope for handing mesh packets off to the routing engine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // lambdas wired by BleService - keeps BLE mechanics separate from business logic
    var onPairingRequestReceived: ((PairingRequest) -> Unit)? = null
    var onPairingAcceptedReceived: ((String) -> Unit)? = null
    var onUnpairRequestReceived: ((String) -> Unit)? = null
    var onSosAlertReceived: ((deviceAddress: String, rawPayload: ByteArray) -> Unit)? = null

    // GATT server callback - entry point for all incoming BLE writes
    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        // logging only - session management lives in BLEScanner
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT connection state changed: device=${device.address} newState=$newState")
        }

        // advertising is gated here - prevents the race condition where a peer connects
        // before the characteristic is registered and ready to receive writes
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT service added status=$status uuid=${service?.uuid} at=${System.currentTimeMillis()}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _isGattServerReady.value = true
                startLeAdvertising()
            }
        }

        // all incoming writes land here - first byte determines the packet type
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean, // true for WRITE_TYPE_DEFAULT, false for WRITE_NO_RESPONSE
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
                            // 0xFF = encrypted payload (message or SOS) - passed up raw,
                            // decryption happens higher up the stack
                            onSosAlertReceived?.invoke(device.address, value)
                            Timber.tag("BLE_ADVERTISER").d("Encrypted direct payload from ${device.address}")
                        }
                        else -> handleMeshMessage(value)
                    }
                }

                // ACK required for WRITE_TYPE_DEFAULT - sending unsolicited would cause an error
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    /**
     * Called when Device B has scanned Device A's QR code and is sending its confirmation back.
     * Strips the prefix byte, parses the JSON payload, and passes the request up via lambda.
     */
    private fun handlePairingRequest(value: ByteArray) {
        Timber.tag("BLE_ADVERTISER").d("handlePairingRequest fired at=${System.currentTimeMillis()}")
        try {
            val jsonString = value.decodeToString(startIndex = 1) // skip prefix byte
            val json = JSONObject(jsonString)
            // sharedKey is present only when the sender scanned the QR code
            val sharedKey = if (json.has("sharedKey") && json.getString("sharedKey").isNotEmpty()) {
                json.getString("sharedKey")
            } else null
            val request = PairingRequest(
                senderDeviceId = json.getString("senderId"),
                senderDisplayName = json.getString("senderName"),
                sharedKey = sharedKey
            )
            onPairingRequestReceived?.invoke(request)
            Timber.tag("BLE_ADVERTISER").d("Pairing request received from ${request.senderDisplayName}")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing request")
        }
    }

    /**
     * Called when the remote device accepted this device's pairing request.
     * Strips the prefix, extracts the sender ID and passes it up.
     */
    private fun handlePairingAccepted(value: ByteArray) {
        try {
            val json = JSONObject(value.decodeToString(startIndex = 1))
            val senderId = json.getString("senderId")
            onPairingAcceptedReceived?.invoke(senderId)
            Timber.tag("BLE_ADVERTISER").d("Pairing accepted by $senderId")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing acceptance")
        }
    }

    /**
     * Called when a friend has removed this device from their friends list.
     * Passes the sender ID up so the local Room record can be cleaned up.
     */
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

    // plaintext SOS fallback - encryption was unavailable, raw bytes passed up as-is
    private fun handleSosAlert(device: BluetoothDevice, value: ByteArray) {
        onSosAlertReceived?.invoke(device.address, value)
        Timber.tag("BLE_ADVERTISER").d("SOS alert received (raw) from ${device.address}")
    }

    // regular mesh relay packet - deserialise and pass to the routing engine on a background thread
    private fun handleMeshMessage(value: ByteArray) {
        val message = serializer.deserialize(value)
        if (message != null) {
            scope.launch {
                meshRoutingEngine.processIncoming(message, TransportType.BLE)
            }
            Timber.tag("BLE_ADVERTISER").d("Handed to routing engine: ${message.messageId}")
        } else {
            Timber.tag("BLE_ADVERTISER").w("Failed to deserialise incoming mesh packet")
        }
    }

    /**
     * Builds the primary GATT service with two characteristics:
     * - discovery: read-only, allows peers to read basic info without a full connection
     * - mesh relay: writable, the entry point for all messages, pairings and SOS alerts
     */
    private fun buildGattService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val discoveryCharacteristic = BluetoothGattCharacteristic(
            DISCOVERY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

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

    /**
     * Opens the GATT server and registers the service. Actual BLE advertising is deferred
     * until [onServiceAdded] fires to prevent the race condition where peers connect before
     * the characteristic is ready. The device ID is stashed so [startLeAdvertising] can
     * read it once the service is confirmed ready.
     */
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

        this.lastMyDeviceId = myDeviceId

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        Timber.tag("BLE_ADVERTISER").d("Adding GATT service at=${System.currentTimeMillis()}")
        gattServer?.addService(buildGattService())
        Timber.tag("BLE_ADVERTISER").d("GATT server started")
    }

    // stashed here so startLeAdvertising() can read it after onServiceAdded fires
    private var lastMyDeviceId: String? = null

    @SuppressLint("MissingPermission")
    private fun startLeAdvertising() {
        val myDeviceId = lastMyDeviceId ?: return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        // low latency + high power for fast discovery in crowded environments
        // setConnectable(true) is critical - without it peers can see this device but not write to it
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // UUID packed as 16 raw bytes - scanners read this to identify the device without connecting
        val deviceIdBytes = try {
            val uuid = UUID.fromString(myDeviceId)
            val buffer = ByteBuffer.allocate(16)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            buffer.array()
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Invalid UUID format: $myDeviceId")
            myDeviceId.take(16).toByteArray(Charsets.UTF_8) // fallback if UUID parsing fails
        }

        // device name and TX power excluded to keep the payload small
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
            gattServer?.close()
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
            Timber.tag("BLE_ADVERTISER").d("Advertising started successfully")
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
            Timber.tag("BLE_ADVERTISER").e("Advertising failed: $errorMsg (code: $errorCode)")
        }
    }

    companion object {
        // shared service UUID - all CrowdLink devices advertise this so scanners know what to look for
        val SERVICE_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
        val DISCOVERY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE9E-0000-1000-8000-00805f9b34fb")
        val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("a8f2e3d1-4b5c-6e7f-8a9b-0c1d2e3f4a5b")

        // first byte of every GATT write - routes the packet before any decryption attempt
        const val PAIRING_REQUEST_PREFIX: Byte = 0x01
        const val PAIRING_ACCEPTED_PREFIX: Byte = 0x02
        const val UNPAIR_REQUEST_PREFIX: Byte = 0x04
        const val SOS_ALERT_PREFIX: Byte = 0x05
        const val ENCRYPTED_PAYLOAD_PREFIX: Byte = 0xFF.toByte()
    }
}