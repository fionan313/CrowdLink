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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MessageViewModel
 *
 * Manages UI state for a single friend's chat screen. The primary send path is the
 * BLE mesh via [MeshRoutingEngine]. Wi-Fi Direct is retained as an optional debug
 * override for testing higher-bandwidth delivery and as a foundation for future
 * multimedia messaging support.
 *
 * Discovery is lifecycle-aware - started in [onResume] and stopped in [onPause]
 * so scanning does not continue after the user leaves the chat screen.
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

    val discoveredFriends = wifiDirectManager.discoveredFriends
    val wifiDirectConnectionInfo = wifiDirectManager.connectionInfo

    private val _isMeshActive = MutableStateFlow(true)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    // shown in the top bar - reflects the best available transport at any moment
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
        meshRoutingEngine.localDeviceId = _myDeviceId.value
    }

    /**
     * Called when the chat screen enters the foreground. Registers Wi-Fi Direct,
     * starts BLE discovery, marks this friend's chat as active to suppress notifications,
     * and launches an auto-connect coroutine for Wi-Fi Direct if the friend is nearby.
     */
    fun onResume(friendId: String) {
        wifiDirectManager.register()
        wifiDirectManager.setupServiceDiscovery(_myDeviceId.value)
        bleScanner.startDiscovery()
        messageRepository.setActiveChatFriend(friendId)

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

    /**
     * Called when the chat screen leaves the foreground. Stops scanning, unregisters
     * Wi-Fi Direct, clears the active chat so notifications resume for this friend.
     */
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
     * Sends a text message to a friend. The message is persisted to Room immediately
     * for instant UI display, then encrypted and enqueued for BLE mesh delivery.
     * If the "wifi_direct_mode" debug flag is set and a peer IP is available,
     * Wi-Fi Direct delivery is attempted first with BLE mesh as the fallback.
     */
    fun sendText(content: String, friendId: String) {
        val myId = _myDeviceId.value

        viewModelScope.launch {
            val forceWifi = sharedPreferences.getBoolean("wifi_direct_mode", false)
            val peerIp = wifiDirectManager.peerIp.value

            // persist immediately so the message appears in the UI without waiting for delivery
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

            // 0x01 prefix identifies this as a text message post-decryption
            val plaintext = byteArrayOf(0x01) + content.toByteArray(Charsets.UTF_8)
            val friend = friendRepository.getFriendById(friendId)

            val payload = if (friend?.sharedKey != null) {
                try {
                    val ciphertext = encryptionManager.encrypt(plaintext, friend.sharedKey)
                    byteArrayOf(BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX) + ciphertext
                } catch (e: Exception) {
                    Timber.tag("MessageViewModel").e(e, "Encryption failed - sending plaintext fallback")
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

            // debug Wi-Fi Direct override - attempt direct delivery, fall back to mesh on failure
            if (forceWifi && peerIp != null) {
                Timber.tag("MessageViewModel").d("Debug: Forcing WiFi Direct delivery to $peerIp")
                val success = wifiDirectManager.deliverMeshMessage(peerIp, meshMessage)
                if (success) {
                    messageRepository.updateMessageStatus(localId, MessageStatus.SENT)
                    return@launch
                }
                Timber.tag("MessageViewModel").w("WiFi Direct forced send failed, falling back to mesh")
            }

            // default path - add to relay queue for BLE mesh delivery
            messageRepository.addToRelayQueue(meshMessage)
        }
    }
}