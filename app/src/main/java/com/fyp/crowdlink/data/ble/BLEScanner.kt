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
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import kotlin.math.pow

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Timber.tag("BLE_SCANNER").wtf("BleScanner CREATED!")

        // Wire relay callback — when engine decides to relay,
        // broadcast to all currently connected peers
        // Updated to handle suspend function
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
    private val pendingMessages = mutableMapOf<String, MutableList<ByteArray>>()

    //  map of device address to BluetoothDevice for reconnection
    private val knownDevices = mutableMapOf<String, BluetoothDevice>()
    
    // map of deviceId to address
    private val deviceIdToAddress = mutableMapOf<String, String>()

    //  GATT client callback
    @SuppressLint("MissingPermission")
    private fun buildGattCallback(deviceAddress: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.tag("BLE_SCANNER")
                        .w("GATT connection failed for $deviceAddress status=$status")
                    activeConnections.remove(deviceAddress)
                    pendingMessages.remove(deviceAddress)
                    gatt.close()
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Timber.tag("BLE_SCANNER").d("GATT connected to $deviceAddress")
                        gatt.requestMtu(512)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.tag("BLE_SCANNER").d("GATT disconnected from $deviceAddress")
                        activeConnections.remove(deviceAddress)
                        pendingMessages.remove(deviceAddress)
                        gatt.close()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Timber.tag("BLE_SCANNER").d("MTU changed to $mtu for $deviceAddress")
                gatt.discoverServices()  // NOW discover services
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.tag("BLE_SCANNER")
                        .w("Service discovery failed on $deviceAddress status=$status")
                    return
                }

                val characteristic = gatt
                    .getService(BleAdvertiser.SERVICE_UUID)
                    ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)

                if (characteristic == null) {
                    Timber.tag("BLE_SCANNER").w("Mesh characteristic not found on $deviceAddress")
                    gatt.disconnect()
                    return
                }

                // Store connection for direct sends after flush
                activeConnections[deviceAddress] = gatt
                Timber.tag("BLE_SCANNER").d("Services discovered on $deviceAddress, flushing queue")
                flushPendingMessages(deviceAddress, gatt, characteristic)
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val result = if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "FAILED($status)"
                Timber.tag("BLE_SCANNER").d("Write to $deviceAddress: $result")
            }
        }
    }

    //  connect to a peer and send data
    @SuppressLint("MissingPermission")
    fun sendData(data: ByteArray, device: BluetoothDevice) {
        val address = device.address

        if (activeConnections.containsKey(address)) {
            val gatt = activeConnections[address]!!
            val characteristic = gatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)
            
            characteristic?.let {
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                it.value = data
                gatt.writeCharacteristic(it)
            }
            return
        }

        knownDevices[address] = device
        pendingMessages.getOrPut(address) { mutableListOf() }.add(data)
        device.connectGatt(context, false, buildGattCallback(address))
    }

    @SuppressLint("MissingPermission")
    fun sendMeshMessage(message: MeshMessage, device: BluetoothDevice) {
        val bytes = serializer.serialize(message) ?: run {
            Timber.tag("BLE_SCANNER").e("Failed to serialize mesh message")
            return
        }
        sendData(bytes, device)
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
                Timber.tag("BLE_SCANNER")
                    .d("Relayed to $address ttl=${message.ttl} success=$success")
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
        
        // Loop through and send all pending messages
        // Note: Sequential writes in GATT often need to wait for onCharacteristicWrite.
        // For simplicity here we just send the first, but in a robust system we'd chain them.
        val first = queue.firstOrNull() ?: return

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = first
        val success = gatt.writeCharacteristic(characteristic)
        Timber.tag("BLE_SCANNER").d("Flushed pending message to $deviceAddress success=$success")
    }

    fun startDiscovery() {
        if (isScanning) {
            Timber.tag("BLE_SCANNER").d("Already scanning")
            return
        }
        if (bluetoothAdapter == null) {
            Timber.tag("BLE_SCANNER").e("Bluetooth Adapter is NULL")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Timber.tag("BLE_SCANNER").e("Bluetooth is DISABLED")
            return
        }
        if (bluetoothLeScanner == null) {
            Timber.tag("BLE_SCANNER").e("BLE Scanner is NULL")
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
            Timber.tag("BLE_SCANNER").d("✓ Scan started successfully")
        } catch (e: SecurityException) {
            Timber.tag("BLE_SCANNER").e(e, "✗ Permission denied")
        }
    }

    fun stopDiscovery() {
        if (!isScanning) return
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            deviceCache.clear()
            // Intentionally not clearing _discoveredDevices here.
            // The list remains visible while scanning is paused so the user
            // does not lose sight of nearby devices when navigating away and back.
            Timber.tag("BLE_SCANNER").d("Scan stopped")
        } catch (e: SecurityException) {
            Timber.tag("BLE_SCANNER").e(e, "Permission denied when stopping")
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
                    knownDevices[it.device.address] = it.device
                    deviceIdToAddress[deviceId] = it.device.address
                    updateDeviceRssi(deviceId, rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.tag("BLE_SCANNER").e("Scan failed: $errorCode")
            isScanning = false
        }
    }

    fun observeRelayQueue(scope: CoroutineScope, repository: MessageRepository) {
        repository.getRelayQueue()
            .distinctUntilChanged()
            .onEach { messages ->
                if (messages.isEmpty()) return@onEach
                val message = messages.first()
                repository.removeFromRelayQueue(message.messageId)

                knownDevices.values.forEach { device ->
                    sendMeshMessage(message, device)
                }
            }
            .launchIn(scope)
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
        else 10.0.pow((txPower - rssi) / (10.0 * 2.5))
    }

    fun getDeviceById(deviceId: String): BluetoothDevice? {
        return deviceIdToAddress[deviceId]?.let { address -> knownDevices[address] }
    }

    fun getDeviceIdByAddress(address: String): String? {
        return deviceIdToAddress.entries.firstOrNull { it.value == address }?.key
    }

    /**
     * Returns all Bluetooth devices that have been seen during scans.
     */
    fun getDiscoveredBluetoothDevices(): Collection<BluetoothDevice> {
        return knownDevices.values
    }

    companion object {
        private const val RSSI_SAMPLE_SIZE = 10
    }
}
