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

/**
 * BleScanner
 *
 * Client side of the BLE mesh. Scans for nearby CrowdLink devices, manages outbound GATT
 * connections, and relays mesh packets to all connected peers. GATT operations are
 * sequentialised through a connection queue to prevent pool saturation (status 133).
 * Exponential backoff is applied after repeated failures to avoid hammering a device
 * that is temporarily unreachable. RSSI readings are smoothed over a rolling window
 * before being used for distance estimation.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {

    init {
        Timber.tag("BLE_SCANNER").wtf("BleScanner CREATED!")

        // wire the relay callback - when the routing engine decides to forward a packet,
        // it calls back here and the packet is written to all currently open connections
        meshRoutingEngine.onRelay = { message ->
            relayToAllPeers(message)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // fetched fresh each time in case BT was toggled off and on
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // last GATT error code + timestamp, exposed to the pairing debug panel
    private val _lastGattError = MutableStateFlow<Pair<Int, Long>?>(null)
    val lastGattError: StateFlow<Pair<Int, Long>?> = _lastGattError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val deviceCache = mutableMapOf<String, MutableList<Int>>() // RSSI rolling window per device
    private var isScanning = false
    private val activeConnections = mutableMapOf<String, BluetoothGatt>()  // open GATT connections by MAC
    private val pendingMessages = mutableMapOf<String, MutableList<ByteArray>>() // queued writes per device
    private val knownDevices = mutableMapOf<String, BluetoothDevice>()     // MAC -> BluetoothDevice for reconnection
    private val deviceIdToAddress = mutableMapOf<String, String>()         // CrowdLink UUID -> MAC address
    private val connectionQueue: ArrayDeque<Pair<BluetoothDevice, ByteArray>> = ArrayDeque() // FIFO send queue
    private var isConnecting = false // gates the queue to one connection attempt at a time
    private val connectionFailureCount = mutableMapOf<String, Int>()
    private val connectionBackoffUntil = mutableMapOf<String, Long>()

    init {
        // periodically evict devices that haven't been seen for 15 seconds
        scope.launch {
            while (true) {
                delay(5000)
                cleanUpOldDevices()
            }
        }
    }

    /**
     * Removes devices from the discovered list that haven't sent a scan result in 15 seconds,
     * and cleans up their cache entries to avoid stale data driving the UI.
     */
    private fun cleanUpOldDevices() {
        val now = System.currentTimeMillis()
        val currentList = _discoveredDevices.value
        val filtered = currentList.filter { now - it.lastSeen < 15_000L }

        if (filtered.size != currentList.size) {
            _discoveredDevices.value = filtered
            val goneIds = currentList.map { it.deviceId } - filtered.map { it.deviceId }.toSet()
            goneIds.forEach { deviceId ->
                deviceCache.remove(deviceId)
                deviceIdToAddress.remove(deviceId)?.let { address ->
                    if (!activeConnections.containsKey(address)) {
                        knownDevices.remove(address)
                    }
                }
            }
        }
    }

    // Android 13 changed the writeCharacteristic API - this wrapper handles both paths
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

    /**
     * Forces Android to re-read the remote GATT service table rather than serving a stale
     * cached version. Prevents "services discovered but characteristic missing" failures.
     */
    @SuppressLint("MissingPermission")
    private fun BluetoothGatt.refreshDeviceCache(): Boolean {
        return try {
            val refreshMethod = this.javaClass.getMethod("refresh")
            refreshMethod.invoke(this) as? Boolean ?: false
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

                        // disconnect -> delay -> close prevents lingering connections causing status 133
                        try {
                            gatt.disconnect()
                            delay(600)
                            gatt.close()
                        } catch (e: Exception) {
                            Timber.tag("BLE_SCANNER").e(e, "Error during GATT cleanup")
                        }

                        val failCount = (connectionFailureCount[deviceAddress] ?: 0) + 1
                        connectionFailureCount[deviceAddress] = failCount
                        val messages = pendingMessages.remove(deviceAddress) ?: emptyList<ByteArray>()

                        if (failCount < MAX_FAILURES_BEFORE_BACKOFF) {
                            // exponential backoff: 2s, 4s, 8s before triggering the long cooldown
                            val backoff = 2000L * (1L shl (failCount - 1))
                            connectionBackoffUntil[deviceAddress] = System.currentTimeMillis() + backoff
                            Timber.tag("BLE_SCANNER").d("Retrying $deviceAddress in ${backoff}ms (attempt $failCount)")
                            val device = knownDevices[deviceAddress]
                            if (device != null) {
                                messages.asReversed().forEach { connectionQueue.addFirst(device to it) }
                            }
                        } else {
                            // max failures reached - enter 30s cooldown
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
                            gatt.refreshDeviceCache() // clear stale service cache before discovering
                            gatt.requestMtu(512)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.tag("BLE_SCANNER").d("GATT disconnected from $deviceAddress")
                            activeConnections.remove(deviceAddress)
                            pendingMessages.remove(deviceAddress)
                            try { gatt.close() } catch (e: Exception) {}
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
                    delay(500) // small delay before service discovery to let the stack settle
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

                // connection is ready - register it, reset failure counts and flush the queue
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

    /**
     * Sends raw bytes to a device. If a GATT connection is already open, writes immediately.
     * Otherwise enqueues the payload and triggers the connection queue processor.
     */
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

    /**
     * Processes the connection queue one entry at a time. Only one GATT connection attempt
     * runs at a time - [isConnecting] gates the rest. Devices in backoff are re-queued
     * rather than skipped permanently so they are retried once the cooldown expires.
     */
    @SuppressLint("MissingPermission")
    private fun processConnectionQueue() {
        if (isConnecting) return
        val next = connectionQueue.removeFirstOrNull() ?: return
        val (device, data) = next
        val address = device.address

        val backoffUntil = connectionBackoffUntil[address] ?: 0L
        if (System.currentTimeMillis() < backoffUntil) {
            // still in cooldown - put back at the end and retry in 1 second
            connectionQueue.addLast(next)
            scope.launch {
                delay(1000)
                processConnectionQueue()
            }
            return
        }

        if (activeConnections.containsKey(address)) {
            // connection became available while this entry was queued - write directly
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
        // TRANSPORT_LE forces BLE rather than classic Bluetooth, avoids status 133 on many devices
        device.connectGatt(context, false, buildGattCallback(address), BluetoothDevice.TRANSPORT_LE)
    }

    fun sendMeshMessage(message: MeshMessage, device: BluetoothDevice) {
        val bytes = serializer.serialize(message) ?: run {
            Timber.tag("BLE_SCANNER").e("Failed to serialize mesh message")
            return
        }
        sendData(bytes, device)
    }

    /**
     * Writes a relay packet to every currently open GATT connection.
     * Called by the routing engine's [onRelay] callback for store-and-forward delivery.
     */
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

    /**
     * Sends the first queued message once a connection is established. Subsequent messages
     * in the queue are sent after the [onCharacteristicWrite] callback confirms delivery.
     */
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
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || bluetoothLeScanner == null) {
            Timber.tag("BLE_SCANNER").e("BLE not available or disabled")
            return
        }

        // filter to CrowdLink's service UUID only - ignores all other BLE devices in range
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleAdvertiser.SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // fast discovery for crowded environments
            .build()

        try {
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            Timber.tag("BLE_SCANNER").d("Scan started successfully")
        } catch (e: SecurityException) {
            Timber.tag("BLE_SCANNER").e(e, "Permission denied")
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
                // the advertiser embeds its CrowdLink UUID as 16 bytes in the service data field
                // read here to identify the device without needing to open a full GATT connection
                val serviceData = it.scanRecord
                    ?.getServiceData(ParcelUuid(BleAdvertiser.SERVICE_UUID))

                val deviceId = serviceData?.let { bytes ->
                    if (bytes.size == 16) {
                        try {
                            val buffer = ByteBuffer.wrap(bytes)
                            UUID(buffer.long, buffer.long).toString()
                        } catch (_: Exception) { null }
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

    /**
     * Observes the Room relay queue and forwards each pending packet to all known devices.
     * Removes the message from the queue immediately after dispatching to prevent duplicate sends.
     */
    fun observeRelayQueue(scope: CoroutineScope, repository: MessageRepository) {
        repository.getRelayQueue()
            .distinctUntilChanged()
            .onEach { messages ->
                if (messages.isEmpty()) return@onEach
                val message = messages.first()
                repository.removeFromRelayQueue(message.messageId)
                knownDevices.values.forEach { device -> sendMeshMessage(message, device) }
            }
            .launchIn(scope)
    }

    /**
     * Adds the new RSSI reading to a rolling window and smooths the result before updating
     * the discovered device list. Raw RSSI is too noisy to use directly for distance.
     */
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

        if (existing >= 0) currentList[existing] = updated else currentList.add(updated)
        _discoveredDevices.value = currentList
    }

    /**
     * Converts a smoothed RSSI reading to an estimated distance in metres using the
     * log-distance path loss model. txPower of -59 dBm at 1m is a standard BLE assumption.
     * The 2.5 path loss exponent is tuned for indoor/crowded environments.
     */
    private fun estimateDistance(rssi: Int): Double {
        return if (rssi == 0) -1.0
        else 10.0.pow((-59 - rssi) / (10.0 * 2.5))
    }

    fun getDeviceById(deviceId: String): BluetoothDevice? {
        return deviceIdToAddress[deviceId]?.let { address -> knownDevices[address] }
    }

    @Suppress("unused")
    fun getDeviceIdByAddress(address: String): String? {
        return deviceIdToAddress.entries.firstOrNull { it.value == address }?.key
    }

    fun getDiscoveredBluetoothDevices(): Collection<BluetoothDevice> = knownDevices.values

    companion object {
        private const val RSSI_SAMPLE_SIZE = 10           // rolling window size for signal smoothing
        private const val MAX_FAILURES_BEFORE_BACKOFF = 3 // attempts before the 30s cooldown kicks in
        private const val BACKOFF_DURATION_MS = 30_000L   // cooldown duration after repeated failure
    }
}