package com.fyp.crowdlink.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {

    init {
        Timber.tag("BLE_SCANNER").wtf("BleScanner CREATED!")

        meshRoutingEngine.onRelay = { message ->
            relayToAllPeers(message)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _lastGattError = MutableStateFlow<Pair<Int, Long>?>(null)
    val lastGattError: StateFlow<Pair<Int, Long>?> = _lastGattError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val deviceCache = mutableMapOf<String, MutableList<Int>>()
    private var isScanning = false

    private val activeConnections = mutableMapOf<String, BluetoothGatt>()
    private val pendingMessages = mutableMapOf<String, MutableList<ByteArray>>()
    private val knownDevices = mutableMapOf<String, BluetoothDevice>()
    private val deviceIdToAddress = mutableMapOf<String, String>()

    private val connectionQueue: ArrayDeque<Pair<BluetoothDevice, ByteArray>> = ArrayDeque()
    private var isConnecting = false
    private val connectionFailureCount = mutableMapOf<String, Int>()
    private val connectionBackoffUntil = mutableMapOf<String, Long>()

    init {
        scope.launch {
            while (true) {
                delay(5000)
                cleanUpOldDevices()
            }
        }
    }

    private fun cleanUpOldDevices() {
        val now = System.currentTimeMillis()
        val timeout = 15_000L // 15 seconds
        
        val currentList = _discoveredDevices.value
        val filtered = currentList.filter { now - it.lastSeen < timeout }
        
        if (filtered.size != currentList.size) {
            _discoveredDevices.value = filtered
            
            // Also clean up local caches for devices that are gone
            val goneDeviceIds = currentList.map { it.deviceId } - filtered.map { it.deviceId }.toSet()
            goneDeviceIds.forEach { deviceId ->
                deviceCache.remove(deviceId)
                deviceIdToAddress.remove(deviceId)?.let { address ->
                    // Only remove if not actively connected
                    if (!activeConnections.containsKey(address)) {
                        knownDevices.remove(address)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.writeChar(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = bytes
            @Suppress("DEPRECATION")
            writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.refreshDeviceCache(): Boolean {
        return try {
            val refreshMethod = this.javaClass.getMethod("refresh")
            val result = refreshMethod.invoke(this) as? Boolean
            result ?: false
        } catch (e: Exception) {
            Timber.tag("BLE_SCANNER").w("GATT cache refresh not available: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildGattCallback(deviceAddress: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _lastGattError.value = status to System.currentTimeMillis()
                        Timber.tag("BLE_SCANNER").w("GATT connection failed for $deviceAddress status=$status")
                        
                        activeConnections.remove(deviceAddress)
                        
                        // Robust cleanup: disconnect -> delay -> close
                        try {
                            gatt.disconnect()
                            delay(600)
                            gatt.close()
                        } catch (e: Exception) {
                            Timber.tag("BLE_SCANNER").e(e, "Error during GATT cleanup")
                        }

                        // Exponential backoff retry logic for Root Causes 1 & 2
                        val failCount = (connectionFailureCount[deviceAddress] ?: 0) + 1
                        connectionFailureCount[deviceAddress] = failCount
                        
                        val messages = pendingMessages.remove(deviceAddress) ?: emptyList<ByteArray>()
                        
                        if (failCount < MAX_FAILURES_BEFORE_BACKOFF) {
                            val backoff = 2000L * (1L shl (failCount - 1))
                            connectionBackoffUntil[deviceAddress] = System.currentTimeMillis() + backoff
                            Timber.tag("BLE_SCANNER").d("Retrying $deviceAddress in ${backoff}ms (attempt $failCount)")
                            
                            val device = knownDevices[deviceAddress]
                            if (device != null) {
                                // Put messages back at the front of the queue
                                messages.asReversed().forEach { connectionQueue.addFirst(device to it) }
                            }
                        } else {
                            connectionBackoffUntil[deviceAddress] = System.currentTimeMillis() + BACKOFF_DURATION_MS
                            connectionFailureCount[deviceAddress] = 0
                            Timber.tag("BLE_SCANNER").w("Device $deviceAddress reached max failures. Backing off for 30s.")
                        }

                        isConnecting = false
                        processConnectionQueue()
                        return@launch
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Timber.tag("BLE_SCANNER").d("GATT connected to $deviceAddress")
                            // Refresh cache to avoid Root Cause 3 (GATT cache corruption)
                            gatt.refreshDeviceCache()
                            gatt.requestMtu(512)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.tag("BLE_SCANNER").d("GATT disconnected from $deviceAddress")
                            activeConnections.remove(deviceAddress)
                            pendingMessages.remove(deviceAddress)
                            
                            try {
                                gatt.close()
                            } catch (e: Exception) {}
                            
                            delay(600)
                            isConnecting = false
                            processConnectionQueue()
                        }
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Timber.tag("BLE_SCANNER").d("MTU changed to $mtu for $deviceAddress")
                scope.launch {
                    delay(500)
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Timber.tag("BLE_SCANNER").w("Service discovery failed on $deviceAddress status=$status")
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

                activeConnections[deviceAddress] = gatt
                connectionFailureCount[deviceAddress] = 0
                connectionBackoffUntil[deviceAddress] = 0L
                isConnecting = false
                processConnectionQueue()
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

    @SuppressLint("MissingPermission")
    fun sendData(data: ByteArray, device: BluetoothDevice) {
        val address = device.address

        if (activeConnections.containsKey(address)) {
            val gatt = activeConnections[address]!!
            val characteristic = gatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID) ?: return
            gatt.writeChar(characteristic, data)
            return
        }

        connectionQueue.addLast(Pair(device, data))
        processConnectionQueue()
    }

    @SuppressLint("MissingPermission")
    private fun processConnectionQueue() {
        if (isConnecting) return
        val next = connectionQueue.removeFirstOrNull() ?: return
        val (device, data) = next
        val address = device.address

        val backoffUntil = connectionBackoffUntil[address] ?: 0L
        if (System.currentTimeMillis() < backoffUntil) {
            // Re-add to end and try again later to avoid busy-looping
            connectionQueue.addLast(next)
            scope.launch {
                delay(1000)
                processConnectionQueue()
            }
            return
        }

        if (activeConnections.containsKey(address)) {
            val gatt = activeConnections[address]!!
            val characteristic = gatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID) ?: run {
                processConnectionQueue()
                return
            }
            gatt.writeChar(characteristic, data)
            processConnectionQueue()
            return
        }
        isConnecting = true
        knownDevices[address] = device
        pendingMessages.getOrPut(address) { mutableListOf() }.add(data)
        Timber.tag("BLE_SCANNER").d("Connecting to $address from queue")
        
        // Use TRANSPORT_LE to help prevent 133 errors on many devices
        device.connectGatt(context, false, buildGattCallback(address), BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun sendMeshMessage(message: MeshMessage, device: BluetoothDevice) {
        val bytes = serializer.serialize(message) ?: run {
            Timber.tag("BLE_SCANNER").e("Failed to serialize mesh message")
            return
        }
        sendData(bytes, device)
    }

    @SuppressLint("MissingPermission")
    private fun relayToAllPeers(message: MeshMessage) {
        val bytes = serializer.serialize(message) ?: return

        activeConnections.forEach { (address, gatt) ->
            val characteristic = gatt
                .getService(BleAdvertiser.SERVICE_UUID)
                ?.getCharacteristic(BleAdvertiser.MESH_CHARACTERISTIC_UUID)

            characteristic?.let {
                gatt.writeChar(it, bytes)
                Timber.tag("BLE_SCANNER").d("Relayed to $address ttl=${message.ttl}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun flushPendingMessages(
        deviceAddress: String,
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val queue = pendingMessages.remove(deviceAddress) ?: return
        val first = queue.firstOrNull() ?: return
        gatt.writeChar(characteristic, first)
        Timber.tag("BLE_SCANNER").d("Flushed pending message to $deviceAddress")
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
                        } catch (_: Exception) {
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

    @Suppress("unused")
    fun getDeviceIdByAddress(address: String): String? {
        return deviceIdToAddress.entries.firstOrNull { it.value == address }?.key
    }

    fun getDiscoveredBluetoothDevices(): Collection<BluetoothDevice> {
        return knownDevices.values
    }

    companion object {
        private const val RSSI_SAMPLE_SIZE = 10
        private const val MAX_FAILURES_BEFORE_BACKOFF = 3
        private const val BACKOFF_DURATION_MS = 30_000L
    }
}
