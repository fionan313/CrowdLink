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
import android.util.Log
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.PairingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        Log.wtf("BLE_ADVERTISER", "BleAdvertiser CREATED!")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var isAdvertising = false
    private var gattServer: BluetoothGattServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var onPairingRequestReceived: ((PairingRequest) -> Unit)? = null
    var onPairingAcceptedReceived: ((String) -> Unit)? = null
    var onUnpairRequestReceived: ((String) -> Unit)? = null

    // GATT server callback handles incoming mesh packets
    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            Log.d("BLE_ADVERTISER", "GATT connection state changed: device=${device.address} newState=$newState")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Log.d("BLE_ADVERTISER", "GATT service added status=$status uuid=${service?.uuid}")
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
        try {
            // Skip prefix byte
            val jsonString = value.decodeToString(startIndex = 1)
            val json = JSONObject(jsonString)
            val request = PairingRequest(
                senderDeviceId = json.getString("senderId"),
                senderDisplayName = json.getString("senderName")
            )
            onPairingRequestReceived?.invoke(request)
            Log.d("BLE_ADVERTISER", "Pairing request received from ${request.senderDisplayName}")
        } catch (e: Exception) {
            Log.e("BLE_ADVERTISER", "Failed to parse pairing request", e)
        }
    }

    private fun handlePairingAccepted(value: ByteArray) {
        try {
            val jsonString = value.decodeToString(startIndex = 1)
            val json = JSONObject(jsonString)
            val senderId = json.getString("senderId")
            onPairingAcceptedReceived?.invoke(senderId)
            Log.d("BLE_ADVERTISER", "Pairing accepted by $senderId")
        } catch (e: Exception) {
            Log.e("BLE_ADVERTISER", "Failed to parse pairing acceptance", e)
        }
    }

    private fun handleUnpairRequest(value: ByteArray) {
        try {
            val json = JSONObject(value.decodeToString(startIndex = 1))
            val senderId = json.getString("senderId")
            onUnpairRequestReceived?.invoke(senderId)
            Log.d("BLE_ADVERTISER", "Unpair request received from $senderId")
        } catch (e: Exception) {
            Log.e("BLE_ADVERTISER", "Failed to parse unpair request", e)
        }
    }

    private fun handleMeshMessage(value: ByteArray) {
        val message = serializer.deserialize(value)
        if (message != null) {
            scope.launch {
                meshRoutingEngine.processIncoming(message)
            }
            Log.d("BLE_ADVERTISER", "Handed to routing engine: ${message.messageId}")
        } else {
            Log.w("BLE_ADVERTISER", "Failed to deserialise incoming mesh packet")
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

        // start GATT server before advertising so it's ready when peers connect
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        gattServer?.addService(buildGattService())
        Log.d("BLE_ADVERTISER", "GATT server started")

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
            Log.e("BLE_ADVERTISER", "Invalid UUID format: $myDeviceId", e)
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
            Log.e("BLE_ADVERTISER", "Permission denied", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()      // close GATT server alongside advertising
            gattServer = null
            isAdvertising = false
            Log.d("BLE_ADVERTISER", "Advertising and GATT server stopped")
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
        val DISCOVERY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000FE9E-0000-1000-8000-00805f9b34fb")
        val MESH_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("a8f2e3d1-4b5c-6e7f-8a9b-0c1d2e3f4a5b")
        
        const val PAIRING_REQUEST_PREFIX: Byte = 0x01
        const val PAIRING_ACCEPTED_PREFIX: Byte = 0x02
        const val UNPAIR_REQUEST_PREFIX: Byte = 0x04
    }
}
