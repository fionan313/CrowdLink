/* data/ble/BleAdvertiser
* This acts as the server side of the BLE Mesh.
*  Making the phone visible to other CrowdLink devices.
* It also handles everything arriving over GATT like pairing, messages, SOS alerts, etc.*/

// package
package com.fyp.crowdlink.data.ble

// imports
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

/* singleton constructor - ensures one instance for the app's lifetime, managed by Hilt
 * prevents multiple instances of the GATT server from running simultaneously */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine, // decides what to do with incoming mesh packets
    private val serializer: MeshMessageSerialiser // converts bytes and MeshMessage objects
) {
    init {
        Timber.tag("BLE_ADVERTISER").wtf("BleAdvertiser CREATED!")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // Bluetooth system service
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter // Bluetooth hardware interface

    private var isAdvertising = false // track whether advertising is active

    // ensures other components wait until the GATT server is actually ready before writing
    private val _isGattServerReady = MutableStateFlow(false)
    val isGattServerReady: StateFlow<Boolean> = _isGattServerReady.asStateFlow()

    private var gattServer: BluetoothGattServer? = null

    // background scope for handing off mesh packets to the routing engine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /* These lambdas are set externally (by BleService)
    * So the advertiser stays focused on BLE mechanics, and the business logic lives elsewhere.*/

    var onPairingRequestReceived: ((PairingRequest) -> Unit)? = null
    var onPairingAcceptedReceived: ((String) -> Unit)? = null
    var onUnpairRequestReceived: ((String) -> Unit)? = null
    var onSosAlertReceived: ((deviceAddress: String, rawPayload: ByteArray) -> Unit)? = null

    // GATT server callback handles incoming mesh packets
    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        // fires whenever a remote device connects or disconnects from the GATT server
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT connection state changed: device=${device.address} newState=$newState")
        }

        /* Don't start advertising until the service is fully added.
        * Avoids a race condition where a peer connects before the GATT server is ready to receive writes.*/
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Timber.tag("BLE_ADVERTISER")
                .d("GATT service added status=$status uuid=${service?.uuid} at=${System.currentTimeMillis()}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _isGattServerReady.value = true
                startLeAdvertising()
            }
        }

        /*Every incoming write from remote devices is handled here.
        * The first byte is used to determine the type of message.*/
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, // device that sent the write
            requestId: Int, // unique ID for the write request
            characteristic: BluetoothGattCharacteristic, // characteristic being written to
            preparedWrite: Boolean,
            responseNeeded: Boolean, // whether to send a response back to the sender
            offset: Int, // offset into the value array
            value: ByteArray // the raw bytes of the payload
        ) {
            if (characteristic.uuid == MESH_CHARACTERISTIC_UUID) {
                if (value.isNotEmpty()) {
                    when (value[0]) {
                        PAIRING_REQUEST_PREFIX -> handlePairingRequest(value)
                        PAIRING_ACCEPTED_PREFIX -> handlePairingAccepted(value)
                        UNPAIR_REQUEST_PREFIX -> handleUnpairRequest(value)
                        SOS_ALERT_PREFIX -> handleSosAlert(device, value)
                        ENCRYPTED_PAYLOAD_PREFIX -> {
                            /* 0xFF = encrypted payload, this could be a message or an SOS alert.
                            * It is passed up as is. Decryption happens higher up the stack. */
                            onSosAlertReceived?.invoke(device.address, value)
                            Timber.tag("BLE_ADVERTISER").d("Encrypted direct payload from ${device.address}")
                        }
                        else -> handleMeshMessage(value)
                    }
                }

                // some writes require an explicit ACK back to the sender
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device, // device that sent the write
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    ) // empty response, confirms receipt
                }
            }
        }
    }

    /* Incoming pairing request. strip the prefix byte, parse the JSON, passed up.
    This is called when Device B has scanned Device A's QR code and is sending its confirmation back over BLE.
     */
    private fun handlePairingRequest(value: ByteArray) {
        Timber.tag("BLE_ADVERTISER").d("handlePairingRequest fired at=${System.currentTimeMillis()}")
        try {
            val jsonString = value.decodeToString(startIndex = 1) // skips prefix byte
            val json = JSONObject(jsonString)
            // sharedKey is optional. Checks if present when the QR code was scanned
            val sharedKey = if (json.has("sharedKey") && json.getString("sharedKey").isNotEmpty()) {
                json.getString("sharedKey")
            } else null
            // construct the PairingRequest object
            val request = PairingRequest(
                senderDeviceId = json.getString("senderId"),
                senderDisplayName = json.getString("senderName"),
                sharedKey = sharedKey
            )
            onPairingRequestReceived?.invoke(request) // passed up
            Timber.tag("BLE_ADVERTISER")
                .d("Pairing request received from ${request.senderDisplayName}")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing request")
        }
    }

    /* The remote device accepted our pairing, strip prefix, parse JSON, extract senderId, pass up
    Called when the user on Device A tapped "Accept" on the pairing dialogue. */
    private fun handlePairingAccepted(value: ByteArray) {
        try {
            val jsonString = value.decodeToString(startIndex = 1)
            val json = JSONObject(jsonString)
            val senderId = json.getString("senderId") // extracts sender ID
            onPairingAcceptedReceived?.invoke(senderId) // passed up
            Timber.tag("BLE_ADVERTISER").d("Pairing accepted by $senderId")
        } catch (e: Exception) {
            Timber.tag("BLE_ADVERTISER").e(e, "Failed to parse pairing acceptance")
        }
    }

    /* Remote device wants to unpair, strip prefix, parse JSON, extract senderId, pass up
    Called when a paired friend has removed the device from their friends list and is notifying over BLE.
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

    // plaintext SOS. encryption failed. Pass the raw bytes up for processing
    private fun handleSosAlert(device: BluetoothDevice, value: ByteArray) {
        onSosAlertReceived?.invoke(device.address, value)
        Timber.tag("BLE_ADVERTISER").d("SOS alert received (raw) from ${device.address}")
    }

    // regular mesh packet — deserialise and pass onto the routing engine, on background thread
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

    /* Two characteristics under one service:
    * - discovery: read-only, peers can read basic info without connecting properly
    * - mesh relay: writable, this is where all messages, pairings and SOS alerts come in */
    private fun buildGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

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

        // Stash the device ID - startLeAdvertising() reads it after onServiceAdded fires
        this.lastMyDeviceId = myDeviceId

        /* GATT server must be open when advertising starts,
        * otherwise peers can connect and write before it's ready to be handled */
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

        /* Low latency + high power mode, to be found quickly at crowded events
        * setConnectable(true) is critical, without it peers can see this device but not write to it. */
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        /* Encode devices UUID as 16 raw bytes to embed in the advertise payload.
        * Scanners read this to identify, without needing to connect. */
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

        // don't include device name or TX power, keeps the payload small
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
        // all devices advertise the same service UUID so scanners know what to look for
        val SERVICE_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
        val DISCOVERY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000FE9E-0000-1000-8000-00805f9b34fb")
        val MESH_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("a8f2e3d1-4b5c-6e7f-8a9b-0c1d2e3f4a5b")

        // First byte of every write, tells what kind of payload it is
        const val PAIRING_REQUEST_PREFIX: Byte = 0x01
        const val PAIRING_ACCEPTED_PREFIX: Byte = 0x02
        const val UNPAIR_REQUEST_PREFIX: Byte = 0x04
        const val SOS_ALERT_PREFIX: Byte = 0x05
        const val ENCRYPTED_PAYLOAD_PREFIX: Byte = 0xFF.toByte()
    }
}
