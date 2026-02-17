package com.fyp.crowdlink.presentation.chat

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.RelayNodeConnection
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
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
 * Now includes relay node support for extended range via ESP32 devices.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sharedPreferences: SharedPreferences,
    private val relayNodeConnection: RelayNodeConnection, // ADDED
) : ViewModel() {

    private val _myDeviceId = MutableStateFlow<String>("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    // Expose the list of discovered WiFi Direct peers to the UI
    val peers = wifiDirectManager.peers

    // Expose the current connection status
    val connectionInfo = wifiDirectManager.connectionInfo

    // Expose the IP address of the connected peer
    val peerIp = wifiDirectManager.peerIp

    // ADDED: Relay connection status
    val isRelayConnected = relayNodeConnection.isConnected

    init {
        _myDeviceId.value = sharedPreferences.getString("device_id", "") ?: ""
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
     * Sends a text message to the currently connected friend.
     *
     * Transmission strategy:
     * 1. Save message to local database
     * 2. Try WiFi Direct first (if peer is directly connected)
     * 3. Fall back to ESP32 relay if WiFi Direct unavailable
     */
    fun sendText(content: String, friendId: String) {
        val myId = _myDeviceId.value

        viewModelScope.launch {
            val message = Message(
                senderId = myId,
                receiverId = friendId,
                content = content,
                isSentByMe = true,
                deliveryStatus = MessageStatus.PENDING
            )

            // 1. Save message to Room database locally
            val messageId = sendMessageUseCase(message)
            val messageWithId = message.copy(id = messageId)

            // 2. Try direct WiFi Direct connection first
            val targetIp = peerIp.value
            if (targetIp != null) {
                // Direct connection available - use WiFi Direct
                wifiDirectManager.sendMessage(targetIp, messageWithId)
            } else if (isRelayConnected.value) {
                // No direct connection - use ESP32 relay
                val relayPayload = "${messageWithId.receiverId}:${messageWithId.content}"
                val success = relayNodeConnection.sendMessage(relayPayload)

                if (!success) {
                    // Mark as failed if relay send fails
                    updateMessageStatus(messageId, MessageStatus.FAILED)
                }
            } else {
                // No connection at all - mark as failed
                updateMessageStatus(messageId, MessageStatus.FAILED)
            }
        }
    }

    /**
     * ADDED: Helper function to update message delivery status
     */
    private suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        // You'll need to add this to your MessageRepository
        // messageRepository.updateMessageStatus(messageId, status)
    }

    /**
     * ADDED: Cleanup relay connection when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        relayNodeConnection.disconnect()
    }
}