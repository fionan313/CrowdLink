package com.fyp.crowdlink.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.GetMessagesUseCase
import com.fyp.crowdlink.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MessageViewModel
 *
 * This ViewModel manages the UI state for the peer-to-peer messaging feature.
 * It now integrates with the MeshRoutingEngine for gossip-based message delivery.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _myDeviceId = MutableStateFlow<String>("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    // Expose the list of discovered WiFi Direct peers to the UI
    val peers = wifiDirectManager.peers

    // Expose the current connection status
    val connectionInfo = wifiDirectManager.connectionInfo

    // Expose the IP address of the connected peer
    val peerIp = wifiDirectManager.peerIp

    init {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
    }

    fun onResume() {
        wifiDirectManager.register()
    }

    fun onPause() {
        wifiDirectManager.unregister()
    }

    fun discover() {
        wifiDirectManager.discoverPeers()
    }

    fun connect(deviceAddress: String) {
        val device = peers.value.find { it.deviceAddress == deviceAddress }
        device?.let {
            wifiDirectManager.connect(it)
        }
    }

    fun disconnect() {
        wifiDirectManager.disconnect()
    }

    fun getMessages(friendId: String): StateFlow<List<Message>> {
        return getMessagesUseCase(friendId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Sends a text message to a friend using the Mesh Routing Engine.
     *
     * Transmission strategy:
     * 1. Save message to local Room database (for UI history)
     * 2. Create a MeshMessage via MeshRoutingEngine
     * 3. Add MeshMessage to the local Relay Queue for BLE/P2P dissemination
     */
    fun sendText(content: String, friendId: String) {
        val myId = _myDeviceId.value

        viewModelScope.launch {
            // 1. Save message to local database for UI
            val localMessage = Message(
                senderId = myId,
                receiverId = friendId,
                content = content,
                isSentByMe = true,
                deliveryStatus = MessageStatus.PENDING
            )
            sendMessageUseCase(localMessage)

            // 2. Create mesh message and add to relay queue for transmission
            val meshMessage = meshRoutingEngine.createOutbound(
                senderId = myId,
                recipientId = friendId,
                payload = content.toByteArray(Charsets.UTF_8)
            )
            
            messageRepository.addToRelayQueue(meshMessage)
        }
    }
}