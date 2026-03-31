package com.fyp.crowdlink.presentation.chat

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleAdvertiser
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.crypto.EncryptionManager
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.GetMessagesUseCase
import com.fyp.crowdlink.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MessageViewModel
 *
 * This ViewModel manages the UI state for the mesh-based messaging feature.
 * It uses the MeshRoutingEngine as the primary send path. Wi-Fi Direct
 * is used automatically for high-bandwidth transfers and direct peer connections.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val bleScanner: BleScanner,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val messageRepository: MessageRepository,
    private val friendRepository: FriendRepository,
    private val encryptionManager: EncryptionManager,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _myDeviceId = MutableStateFlow("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    // Expose discovered Wi-Fi Direct friends (mapped by device ID)
    val discoveredFriends = wifiDirectManager.discoveredFriends

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

    private var autoConnectJob: Job? = null

    init {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
        // Ensure routing engine has the correct ID for header generation
        meshRoutingEngine.localDeviceId = _myDeviceId.value
    }

    fun onResume(friendId: String) {
        wifiDirectManager.register()
        wifiDirectManager.setupServiceDiscovery(_myDeviceId.value)
        bleScanner.startDiscovery()
        messageRepository.setActiveChatFriend(friendId)

        // Establish WiFi Direct connection automatically if the friend is nearby
        autoConnectJob?.cancel()
        autoConnectJob = viewModelScope.launch {
            wifiDirectManager.discoveredFriends.collect { friends ->
                val device = friends[friendId]
                if (device != null) {
                    val info = wifiDirectManager.connectionInfo.value
                    if (info == null || !info.groupFormed) {
                        Timber.tag("MessageViewModel").d("Auto-connecting to friend $friendId via WiFi Direct")
                        wifiDirectManager.connect(device)
                    }
                }
            }
        }
    }

    fun onPause() {
        wifiDirectManager.unregister()
        bleScanner.stopDiscovery()
        messageRepository.setActiveChatFriend(null)
        autoConnectJob?.cancel()
    }

    fun discover() {
        wifiDirectManager.discoverServices()
        bleScanner.startDiscovery()
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
     * The engine adds it to the relay queue, which is then observed by BLE mesh transport.
     * WiFi Direct is bypassed for small text messages unless the debug "wifi_direct_mode" is enabled.
     */
    fun sendText(content: String, friendId: String) {
        val myId = _myDeviceId.value

        viewModelScope.launch {
            // Check for Debug WiFi Direct Override
            val forceWifi = sharedPreferences.getBoolean("wifi_direct_mode", false)
            val peerIp = wifiDirectManager.peerIp.value

            // 1. Save to local database for UI display immediately.
            // Since this is an outgoing message (0 hops), we label it with the physical transport.
            val localMessage = Message(
                senderId = myId,
                receiverId = friendId,
                content = content,
                isSentByMe = true,
                deliveryStatus = MessageStatus.PENDING,
                hopCount = 0,
                transportType = if (forceWifi && peerIp != null) TransportType.WIFI else TransportType.BLE
            )
            val localId = sendMessageUseCase(localMessage)

            // 2. Hand off to MeshRoutingEngine with type prefix (0x01 for text)
            val plaintext = byteArrayOf(0x01) + content.toByteArray(Charsets.UTF_8)
            val friend = friendRepository.getFriendById(friendId)

            val payload = if (friend?.sharedKey != null) {
                try {
                    val ciphertext = encryptionManager.encrypt(plaintext, friend.sharedKey)
                    byteArrayOf(BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX) + ciphertext
                } catch (e: Exception) {
                    Timber.tag("MessageViewModel").e(e, "Encryption failed — sending plaintext fallback")
                    plaintext
                }
            } else {
                plaintext
            }

            val meshMessage = meshRoutingEngine.createOutbound(
                senderId = myId,
                recipientId = friendId,
                payload = payload
            )
            
            if (forceWifi && peerIp != null) {
                Timber.tag("MessageViewModel").d("Debug: Forcing WiFi Direct delivery to $peerIp")
                val success = wifiDirectManager.deliverMeshMessage(peerIp, meshMessage)
                if (success) {
                    messageRepository.updateMessageStatus(localId, MessageStatus.SENT)
                    return@launch
                }
                Timber.tag("MessageViewModel").w("WiFi Direct forced send failed, falling back to Mesh")
            }

            // 3. Default path: Persist to relay queue for BLE mesh delivery
            messageRepository.addToRelayQueue(meshMessage)
        }
    }
}
