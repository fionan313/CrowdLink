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
import android.util.Log
import com.fyp.crowdlink.domain.model.Message
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
            override fun onSuccess() { Log.d("WifiDirect", "Discovery Started") }
            override fun onFailure(reason: Int) { Log.e("WifiDirect", "Discovery Failed: $reason") }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d("WifiDirect", "Connect Success") }
            override fun onFailure(reason: Int) { Log.e("WifiDirect", "Connect Failed: $reason") }
        })
    }

    /**
     * Kills all active WiFi Direct connections and removes the current group.
     * This is a debug tool to reset the P2P state.
     */
    fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirect", "Group removed successfully")
                _connectionInfo.value = null
                _peerIp.value = null
                stopServer()
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirect", "Failed to remove group: $reason")
                // Even if removeGroup fails, we might still be in a middle of connecting
                manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Log.d("WifiDirect", "Connect cancelled") }
                    override fun onFailure(reason: Int) { Log.e("WifiDirect", "Failed to cancel connect: $reason") }
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
                Log.d("WifiDirect", "Server started on port 8888")
                while (true) {
                    val client = serverSocket.accept()
                    val clientIp = client.inetAddress.hostAddress
                    Log.d("WifiDirect", "Accepted connection from $clientIp")
                    
                    // Store the IP of the client that just connected
                    _peerIp.value = clientIp
                    
                    handleIncomingConnection(client)
                }
            } catch (e: Exception) {
                Log.e("WifiDirect", "Server Error", e)
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                val senderId = input.readUTF()
                val content = input.readUTF()
                
                Log.d("WifiDirect", "Received message from $senderId: $content")

                if (content != "HANDSHAKE_INIT") {
                    val message = Message(
                        senderId = senderId,
                        receiverId = "me", // Mapping to local user
                        content = content,
                        isSentByMe = false
                    )
                    messageRepository.sendMessage(message)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("WifiDirect", "Receive Error", e)
            }
        }
    }

    private fun sendHandshake(targetAddress: String) {
        scope.launch {
            // Wait a moment for the server on the other end to start
            delay(1000)
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, 8888), 5000)
                val output = DataOutputStream(socket.getOutputStream())
                output.writeUTF("CLIENT_HANDSHAKE")
                output.writeUTF("HANDSHAKE_INIT")
                output.flush()
                socket.close()
                Log.d("WifiDirect", "Handshake sent to $targetAddress")
            } catch (e: Exception) {
                Log.e("WifiDirect", "Handshake failed", e)
            }
        }
    }

    fun sendMessage(targetAddress: String, message: Message) {
        scope.launch {
            try {
                Log.d("WifiDirect", "Attempting to send message to $targetAddress")
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, 8888), 5000)
                val output = DataOutputStream(socket.getOutputStream())
                output.writeUTF(message.senderId)
                output.writeUTF(message.content)
                output.flush()
                socket.close()
                Log.d("WifiDirect", "Message sent successfully")
            } catch (e: Exception) {
                Log.e("WifiDirect", "Send Error", e)
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        serverJob = null
    }
}
