package com.fyp.crowdlink.data.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import androidx.annotation.RequiresPermission
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val serializer: MeshMessageSerialiser
) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers = _peers.asStateFlow()

    // Maps discovered deviceId to its WifiP2pDevice
    private val _discoveredFriends = MutableStateFlow<Map<String, WifiP2pDevice>>(emptyMap())
    val discoveredFriends = _discoveredFriends.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo = _connectionInfo.asStateFlow()

    private val _peerIp = MutableStateFlow<String?>(null)
    val peerIp = _peerIp.asStateFlow()

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        _peers.value = peerList.deviceList.toList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager?.requestConnectionInfo(channel) { info ->
                        _connectionInfo.value = info
                        if (info.groupFormed) {
                            startServer(info)
                            if (!info.isGroupOwner) {
                                _peerIp.value = info.groupOwnerAddress.hostAddress
                                sendHandshake(info.groupOwnerAddress.hostAddress)
                            }
                        } else {
                            stopServer()
                            _peerIp.value = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Set up local service to advertise our device ID over WiFi Direct.
     */
    fun setupServiceDiscovery(myDeviceId: String) {
        val record = mapOf("deviceId" to myDeviceId)
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            "CrowdLink_$myDeviceId", "_crowdlink._tcp", record
        )

        manager?.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Timber.tag("WifiDirect").d("Local service added: $myDeviceId") }
                    override fun onFailure(reason: Int) { Timber.tag("WifiDirect").e("Failed to add local service: $reason") }
                })
            }
            override fun onFailure(reason: Int) { Timber.tag("WifiDirect").e("Failed to clear local services: $reason") }
        })
    }

    /**
     * Start discovering other CrowdLink services nearby.
     */
    @SuppressLint("MissingPermission")
    fun discoverServices() {
        val request = WifiP2pDnsSdServiceRequest.newInstance()
        
        manager?.setDnsSdResponseListeners(channel, 
            { _, _, _ -> },
            { _, txtRecordMap, srcDevice ->
                val deviceId = txtRecordMap["deviceId"]
                if (deviceId != null) {
                    Timber.tag("WifiDirect").d("Discovered friend $deviceId at ${srcDevice.deviceAddress}")
                    val currentMap = _discoveredFriends.value.toMutableMap()
                    currentMap[deviceId] = srcDevice
                    _discoveredFriends.value = currentMap
                }
            }
        )

        manager?.addServiceRequest(channel, request, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Timber.tag("WifiDirect").d("Service discovery started") }
                    override fun onFailure(reason: Int) { Timber.tag("WifiDirect").e("Service discovery failed: $reason") }
                })
            }
            override fun onFailure(reason: Int) { Timber.tag("WifiDirect").e("Add service request failed: $reason") }
        })
    }

    suspend fun deliverMeshMessage(targetAddress: String, meshMessage: com.fyp.crowdlink.domain.model.MeshMessage): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, 8888), 2000)
                val output = DataOutputStream(socket.getOutputStream())
                
                val bytes = serializer.serialize(meshMessage) ?: return@withContext false
                
                output.writeUTF("MESH_PACKET_V2")
                output.writeInt(bytes.size)
                output.write(bytes)
                
                output.flush()
                socket.close()
                true
            } catch (e: Exception) {
                Timber.tag("WifiDirect").e(e, "Failed to deliver mesh message to $targetAddress")
                false
            }
        }
    }

    fun register() {
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
        stopServer()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Timber.tag("WifiDirect").d("Connect Success") }
            override fun onFailure(reason: Int) { Timber.tag("WifiDirect").e("Connect Failed: $reason") }
        })
    }

    fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.tag("WifiDirect").d("Group removed successfully")
                _connectionInfo.value = null
                _peerIp.value = null
                stopServer()
            }
            override fun onFailure(reason: Int) {
                Timber.tag("WifiDirect").e("Failed to remove group: $reason")
            }
        })
    }

    private fun startServer(info: WifiP2pInfo) {
        if (serverJob == null) {
            serverJob = scope.launch {
                runServer()
            }
        }
    }

    private suspend fun runServer() {
        withContext(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(8888)
                while (true) {
                    val client = serverSocket.accept()
                    val clientIp = client.inetAddress.hostAddress
                    _peerIp.value = clientIp
                    handleIncomingConnection(client)
                }
            } catch (e: Exception) {
                Timber.tag("WifiDirect").e(e, "Server Error")
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                val header = input.readUTF()
                
                when (header) {
                    "MESH_PACKET_V2" -> {
                        val size = input.readInt()
                        val bytes = ByteArray(size)
                        input.readFully(bytes)
                        val meshMessage = serializer.deserialize(bytes)
                        if (meshMessage != null) {
                            meshRoutingEngine.processIncoming(meshMessage, TransportType.WIFI)
                        }
                    }
                    "MESH_RELAY_V1" -> {
                        val senderId = input.readUTF()
                        val content = input.readUTF()
                        val messageIdStr = input.readUTF()
                        
                        val message = Message(
                            messageId = messageIdStr,
                            senderId = senderId,
                            receiverId = meshRoutingEngine.localDeviceId,
                            content = content,
                            isSentByMe = false,
                            transportType = TransportType.WIFI
                        )
                        messageRepository.sendMessage(message)
                    }
                    else -> {
                        val senderId = header
                        val content = input.readUTF()
                        if (content != "HANDSHAKE_INIT") {
                            val message = Message(
                                senderId = senderId,
                                receiverId = meshRoutingEngine.localDeviceId,
                                content = content,
                                isSentByMe = false,
                                transportType = TransportType.WIFI
                            )
                            messageRepository.sendMessage(message)
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Timber.tag("WifiDirect").e(e, "Receive Error")
            }
        }
    }

    private fun sendHandshake(targetAddress: String) {
        scope.launch {
            delay(1000)
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, 8888), 5000)
                val output = DataOutputStream(socket.getOutputStream())
                output.writeUTF("CLIENT_HANDSHAKE")
                output.writeUTF("HANDSHAKE_INIT")
                output.flush()
                socket.close()
            } catch (e: Exception) {
                Timber.tag("WifiDirect").e(e, "Handshake failed")
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        serverJob = null
    }
}
