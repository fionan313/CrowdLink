package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.MeshMessage
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
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {
    init {
        android.util.Log.wtf("BLE_SCANNER", "BleScanner CREATED!")

        // Wire relay callback — when engine decides to relay,
        // broadcast to all currently connected peers
        meshRoutingEngine.onRelay = { message ->
            relayToAllPeers(message)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val deviceCache = mutableMapOf<String, MutableList<Int>>()
    private var isScanning = false

    //  active GATT connections keyed by device address
    private val activeConnections = mutableMapOf<String, BluetoothGatt>()

    //  messages queued while waiting for connection
    private val pendingMessages = mutableMapOf<String, MutableList<MeshMessage>>()

    //  map of device address to BluetoothDevice for reconnection
    private val knownDevices = mutableMapOf<String, BluetoothDevice>()

    //  GATT client callback
    @SuppressLint("MissingPermission")
    private fun buildGattCallback(deviceAddress: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BLE_SCANNER", "GATT connected to $deviceAddress")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BLE_SCANNER", "GATT disconnected from $deviceAddress")
                        activeConnections.remove(deviceAddress)
                        gatt.close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w("BLE_SCANNER", "Service discovery failed on $deviceAddress status=$status")
                    return
                }

                val characteristic = gatt
                    .getService(BleAdvertiser.SERVICE_UUID)
                    ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)

                if (characteristic == null) {
                    Log.w("BLE_SCANNER", "Mesh characteristic not found on $deviceAddress")
                    gatt.disconnect()
                    return
                }

                activeConnections[deviceAddress] = gatt
                Log.d("BLE_SCANNER", "Services discovered on $deviceAddress, flushing queue")
                flushPendingMessages(deviceAddress, gatt, characteristic)
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val result = if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "FAILED($status)"
                Log.d("BLE_SCANNER", "Write to $deviceAddress: $result")
            }
        }
    }

    //  connect to a peer and send a mesh message
    @SuppressLint("MissingPermission")
    fun sendMeshMessage(message: MeshMessage, device: BluetoothDevice) {
        val address = device.address
        knownDevices[address] = device

        val bytes = serializer.serialize(message) ?: run {
            Log.e("BLE_SCANNER", "Failed to serialize mesh message")
            return
        }

        val existingGatt = activeConnections[address]
        if (existingGatt != null) {
            val characteristic = existingGatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)

            characteristic?.let {
                it.value = bytes
                existingGatt.writeCharacteristic(it)
                Log.d("BLE_SCANNER", "Sent mesh message directly to $address")
            }
        } else {
            pendingMessages.getOrPut(address) { mutableListOf() }.add(message)
            Log.d("BLE_SCANNER", "Queuing message, connecting to $address")
            device.connectGatt(context, false, buildGattCallback(address))
        }
    }

    //  relay a message to all currently connected peers
    @SuppressLint("MissingPermission")
    private fun relayToAllPeers(message: MeshMessage) {
        val bytes = serializer.serialize(message) ?: return

        activeConnections.forEach { (address, gatt) ->
            val characteristic = gatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)

            characteristic?.let {
                it.value = bytes
                val success = gatt.writeCharacteristic(it)
                Log.d("BLE_SCANNER", "Relayed to $address ttl=${message.ttl} success=$success")
            }
        }
    }

    //  flush queued messages after connection established
    @SuppressLint("MissingPermission")
    private fun flushPendingMessages(
        deviceAddress: String,
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val queue = pendingMessages.remove(deviceAddress) ?: return
        val first = queue.firstOrNull() ?: return
        val bytes = serializer.serialize(first) ?: return

        characteristic.value = bytes
        val success = gatt.writeCharacteristic(characteristic)
        Log.d("BLE_SCANNER", "Flushed pending message to $deviceAddress success=$success")
    }

    // --- Existing methods below — unchanged ---

    fun startDiscovery() {
        if (isScanning) {
            Log.d("BLE_SCANNER", "Already scanning")
            return
        }
        if (bluetoothAdapter == null) {
            Log.e("BLE_SCANNER", "Bluetooth Adapter is NULL")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BLE_SCANNER", "Bluetooth is DISABLED")
            return
        }
        if (bluetoothLeScanner == null) {
            Log.e("BLE_SCANNER", "BLE Scanner is NULL")
            return
        }

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
                val serviceData = it.scanRecord
                    ?.getServiceData(ParcelUuid(BleAdvertiser.SERVICE_UUID))

                val deviceId = serviceData?.let { bytes ->
                    if (bytes.size == 16) {
                        try {
                            val buffer = ByteBuffer.wrap(bytes)
                            val msb = buffer.long
                            val lsb = buffer.long
                            UUID(msb, lsb).toString()
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                if (deviceId != null) {
                    //  store BluetoothDevice for later GATT connections
                    knownDevices[it.device.address] = it.device
                    updateDeviceRssi(deviceId, rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_SCANNER", "Scan failed: $errorCode")
            isScanning = false
        }
    }

    private fun updateDeviceRssi(deviceId: String, rssi: Int) {
        val readings = deviceCache.getOrPut(deviceId) { mutableListOf() }
        readings.add(rssi)
        if (readings.size > RSSI_SAMPLE_SIZE) readings.removeAt(0)

        val smoothedRssi = readings.average().toInt()
        val distance = estimateDistance(smoothedRssi)

        val currentList = _discoveredDevices.value.toMutableList()
        val existing = currentList.indexOfFirst { it.deviceId == deviceId }

        val updated = DiscoveredDevice(
            deviceId = deviceId,
            rssi = smoothedRssi,
            estimatedDistance = distance,
            lastSeen = System.currentTimeMillis()
        )

        if (existing >= 0) currentList[existing] = updated
        else currentList.add(updated)

        _discoveredDevices.value = currentList
    }

    private fun estimateDistance(rssi: Int): Double {
        val txPower = -59
        return if (rssi == 0) -1.0
        else Math.pow(10.0, (txPower - rssi) / (10.0 * 2.5))
    }

    companion object {
        private const val RSSI_SAMPLE_SIZE = 10
    }
}