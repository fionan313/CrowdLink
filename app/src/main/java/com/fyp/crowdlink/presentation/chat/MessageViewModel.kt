package com.fyp.crowdlink.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.GetMessagesUseCase
import com.fyp.crowdlink.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MessageViewModel
 *
 * This ViewModel manages the UI state for the mesh-based messaging feature.
 * It uses the MeshRoutingEngine as the primary send path, with Wi-Fi Direct
 * and ESP32 acting as background fallback transports observing the relay queue.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val bleScanner: BleScanner,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _myDeviceId = MutableStateFlow("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    // Expose the list of discovered Wi-Fi Direct peers for connection setup
    val peers = wifiDirectManager.peers

    // Expose connection info for status display
    val wifiDirectConnectionInfo = wifiDirectManager.connectionInfo

    // Mesh status - Active if we are scanning for other BLE mesh devices
    private val _isMeshActive = MutableStateFlow(true)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    // Combined discovery status for the UI
    val discoveryStatus: StateFlow<String> = combine(
        wifiDirectConnectionInfo,
        bleScanner.discoveredDevices
    ) { wifi, bleDevices ->
        when {
            wifi?.groupFormed == true -> "Connected (WiFi Direct)"
            bleDevices.isNotEmpty() -> "Mesh Active (${bleDevices.size} peers)"
            else -> "Searching for peers..."
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Initialising...")

    init {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
        // Ensure routing engine has the correct ID for header generation
        meshRoutingEngine.localDeviceId = _myDeviceId.value
    }

    fun onResume() {
        wifiDirectManager.register()
        bleScanner.startDiscovery()
    }

    fun onPause() {
        wifiDirectManager.unregister()
        bleScanner.stopDiscovery()
    }

    fun discover() {
        wifiDirectManager.discoverPeers()
        bleScanner.startDiscovery()
    }

    fun connect(deviceAddress: String) {
        val device = peers.value.find { it.deviceAddress == deviceAddress }
        device?.let {
            wifiDirectManager.connect(it)
        }
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
     * Sends a text message via the Mesh Routing Engine.
     * The engine adds it to the relay queue, which is then observed by:
     * 1. BleScanner (for BLE Mesh relay)
     * 2. WifiDirectManager (for Wi-Fi fallback)
     * 3. RelayNodeConnection (for ESP32 fallback)
     */
    fun sendText(content: String, friendId: String) {
        val myId = _myDeviceId.value

        Timber.tag("MessageViewModel").d("Adding to relay queue: $content")

        viewModelScope.launch {
            // 1. Save to local database for UI display immediately
            val localMessage = Message(
                senderId = myId,
                receiverId = friendId,
                content = content,
                isSentByMe = true,
                deliveryStatus = MessageStatus.PENDING,
                hopCount = 0,
                transportType = TransportType.MESH
            )
            sendMessageUseCase(localMessage)

            // 2. Hand off to MeshRoutingEngine with type prefix (0x01 for text)
            val payload = byteArrayOf(0x01) + content.toByteArray(Charsets.UTF_8)

            val meshMessage = meshRoutingEngine.createOutbound(
                senderId = myId,
                recipientId = friendId,
                payload = payload
            )
            
            // 3. Persist to relay queue - background transports will handle the rest
            messageRepository.addToRelayQueue(meshMessage)
        }
    }
}
