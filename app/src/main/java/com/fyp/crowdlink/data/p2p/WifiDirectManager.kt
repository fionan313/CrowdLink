package com.fyp.crowdlink.data.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
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
    private val messageRepository: MessageRepository
) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo = _connectionInfo.asStateFlow()

    // Store the IP address of the connected peer
    private val _peerIp = MutableStateFlow<String?>(null)
    val peerIp = _peerIp.asStateFlow()

    private var serverJob: Job? = null
    private var relayObserverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    init {
        startRelayQueueObserver()
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
                            // If I am the client, I know the GO's IP. I should send a handshake
                            // so the GO knows my IP.
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
     * Observes the relay queue and attempts to deliver messages via Wi-Fi Direct
     * if a peer is currently connected.
     */
    private fun startRelayQueueObserver() {
        relayObserverJob?.cancel()
        relayObserverJob = scope.launch {
            messageRepository.getRelayQueue().collect { queue ->
                val targetIp = _peerIp.value
                if (targetIp != null && queue.isNotEmpty()) {
                    Timber.tag("WifiDirect")
                        .d("Relay queue update: ${queue.size} messages waiting, peer connected at $targetIp")
                    queue.forEach { meshMessage ->
                        // Attempt delivery via Wi-Fi Direct fallback
                        val success = attemptWifiDirectDelivery(targetIp, meshMessage)
                        if (success) {
                            Timber.tag("WifiDirect")
                                .d("Successfully delivered message ${meshMessage.messageId} via WiFi Direct, removing from queue")
                            messageRepository.removeFromRelayQueue(meshMessage.messageId)
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper to wrap mesh message for Wi-Fi Direct transport
     */
    private suspend fun attemptWifiDirectDelivery(targetAddress: String, meshMessage: com.fyp.crowdlink.domain.model.MeshMessage): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, 8888), 2000)
                val output = DataOutputStream(socket.getOutputStream())
                
                // We send a special prefix to indicate this is a MeshMessage payload
                output.writeUTF("MESH_RELAY_V1")
                output.writeUTF(meshMessage.senderId)
                // Sending the content as a string for now as the existing WifiDirectManager expects strings,
                // but in a real mesh we might send the raw payload.
                output.writeUTF(String(meshMessage.payload, Charsets.UTF_8))
                output.writeUTF(meshMessage.messageId.toString())
                
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
    fun discoverPeers() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.tag("WifiDirect").d("Discovery Started") }
            override fun onFailure(reason: Int) {
                Timber.tag("WifiDirect").e("Discovery Failed: $reason") }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.tag("WifiDirect").d("Connect Success") }
            override fun onFailure(reason: Int) {
                Timber.tag("WifiDirect").e("Connect Failed: $reason") }
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
                manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Timber.tag("WifiDirect").d("Connect cancelled") }
                    override fun onFailure(reason: Int) {
                        Timber.tag("WifiDirect").e("Failed to cancel connect: $reason") }
                })
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
                Timber.tag("WifiDirect").d("Server started on port 8888")
                while (true) {
                    val client = serverSocket.accept()
                    val clientIp = client.inetAddress.hostAddress
                    Timber.tag("WifiDirect").d("Accepted connection from $clientIp")
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
                
                if (header == "MESH_RELAY_V1") {
                    val senderId = input.readUTF()
                    val content = input.readUTF()
                    input.readUTF()

                    Timber.tag("WifiDirect").d("Received MESH message from $senderId: $content")
                    
                    // In a real mesh refactor, we would reconstruct the MeshMessage 
                    // and hand it to MeshRoutingEngine.processIncoming().
                    // For now, to maintain functionality, we'll just insert as a domain message.
                    val message = Message(
                        senderId = senderId,
                        receiverId = "me",
                        content = content,
                        isSentByMe = false,
                        transportType = TransportType.WIFI
                    )
                    messageRepository.sendMessage(message)
                    // TODO: Notify MeshRoutingEngine so it marks it as seen
                } else {
                    // Legacy handling
                    val senderId = header // In legacy, first string was senderId
                    val content = input.readUTF()
                    
                    if (content != "HANDSHAKE_INIT") {
                        val message = Message(
                            senderId = senderId,
                            receiverId = "me",
                            content = content,
                            isSentByMe = false,
                            transportType = TransportType.WIFI
                        )
                        messageRepository.sendMessage(message)
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
                Timber.tag("WifiDirect").d("Handshake sent to $targetAddress")
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
