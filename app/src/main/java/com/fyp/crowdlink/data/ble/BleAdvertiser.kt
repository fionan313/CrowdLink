package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BleAdvertiser
 *
 * This class is responsible for broadcasting the local device's presence using Bluetooth Low Energy (BLE) Advertising
 * and managing the GATT server for mesh relaying.
 */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false

    /**
     * Starts advertising the local device's presence and initializes the GATT server.
     *
     * @param myDeviceId The unique UUID string of the local device.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(myDeviceId: String) {
        Timber.tag(TAG).d("Starting advertising and GATT server with device ID: $myDeviceId")

        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Timber.tag(TAG).e("Bluetooth LE Advertiser not available")
            return
        }

        if (isAdvertising) {
            Timber.tag(TAG).d("Already advertising, stopping first")
            stopAdvertising()
        }

        // Initialize GATT Server
        setupGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // Must be connectable to allow mesh relay writes
            .setTimeout(0)
            .build()

        val deviceIdBytes = try {
            val uuid = UUID.fromString(myDeviceId)
            val buffer = ByteBuffer.allocate(16)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            buffer.array()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Invalid UUID format: $myDeviceId")
            myDeviceId.take(16).toByteArray(Charsets.UTF_8)
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), deviceIdBytes)
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()
            gattServer = null
            isAdvertising = false
            Timber.tag(TAG).d("Advertising and GATT server stopped")
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Permission denied when stopping")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        if (gattServer != null) return

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        gattServer?.addService(buildGattService())
    }

    private fun buildGattService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Existing discovery characteristic
        val discoveryCharacteristic = BluetoothGattCharacteristic(
            DISCOVERY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // NEW — mesh relay characteristic, writable by remote devices
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

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
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
                Timber.tag(TAG).d("Received mesh write from ${device.address}")
                
                // Hand off to routing engine
                val message = serializer.deserialize(value)
                if (message != null) {
                    meshRoutingEngine.processIncoming(message)
                }

                // Acknowledge the write if the client expects a response
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

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Timber.tag(TAG).d("✓ Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Timber.tag(TAG).e("✗ Advertising failed: $errorCode")
        }
    }

    companion object {
        private const val TAG = "BLE_ADVERTISER"
        val SERVICE_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
        val DISCOVERY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE9F-0000-1000-8000-00805f9b34fb")
        val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("a8f2e3d1-4b5c-6e7f-8a9b-0c1d2e3f4a5b")
    }
}
