package com.fyp.crowdlink.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.usecase.GetMessagesUseCase
import com.fyp.crowdlink.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MessageViewModel
 *
 * This ViewModel manages the UI state for the peer-to-peer messaging feature.
 * It coordinates with the [WifiDirectManager] for network connectivity and uses
 * Clean Architecture Use Cases to interact with the message database.
 */
@HiltViewModel
class MessageViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase
) : ViewModel() {

    // Expose the list of discovered WiFi Direct peers to the UI
    val peers = wifiDirectManager.peers
    
    // Expose the current connection status (connected, group owner, etc.)
    val connectionInfo = wifiDirectManager.connectionInfo

    /**
     * Registers the WiFi Direct broadcast receiver.
     * This should be called from the UI's onResume lifecycle event.
     */
    fun onResume() {
        wifiDirectManager.register()
    }

    /**
     * Unregisters the WiFi Direct broadcast receiver.
     * This should be called from the UI's onPause lifecycle event.
     */
    fun onPause() {
        wifiDirectManager.unregister()
    }

    /**
     * Triggers a search for nearby WiFi Direct-enabled devices.
     */
    fun discover() {
        wifiDirectManager.discoverPeers()
    }

    /**
     * Connects to a specific peer device using its MAC address.
     */
    fun connect(deviceAddress: String) {
        val device = peers.value.find { it.deviceAddress == deviceAddress }
        device?.let {
            wifiDirectManager.connect(it)
        }
    }

    /**
     * Returns a stateful flow of messages for a specific friend.
     * The UI should collect this to display the chat history.
     */
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
     * It first saves the message locally to Room, then attempts to transmit it via Socket.
     *
     * @param content The text content of the message.
     * @param friendId The ID of the friend the message is being sent to.
     * @param myDeviceId The local user's unique device ID.
     */
    fun sendText(content: String, friendId: String, myDeviceId: String) {
        val info = connectionInfo.value ?: return
        if (!info.groupFormed) return

        // Determine the target IP address based on WiFi Direct Group roles.
        // If we are not the Group Owner (GO), we send to the GO's address.
        // If we ARE the GO, we send to the client (default P2P subnet IP).
        val targetIp = if (!info.isGroupOwner) {
            info.groupOwnerAddress.hostAddress
        } else {
            "192.168.49.1" // Default IP for the first client in an Android P2P group
        }

        viewModelScope.launch {
            val message = Message(
                senderId = myDeviceId,
                receiverId = friendId,
                content = content,
                isSentByMe = true,
                deliveryStatus = MessageStatus.PENDING
            )

            // 1. Save message to Room database locally
            val messageId = sendMessageUseCase(message)

            // 2. Transmit the message data over a raw TCP socket via WifiDirectManager
            targetIp?.let { ip ->
                wifiDirectManager.sendMessage(ip, message.copy(id = messageId))
            }
        }
    }
}
