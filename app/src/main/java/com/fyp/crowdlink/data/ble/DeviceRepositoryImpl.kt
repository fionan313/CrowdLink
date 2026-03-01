package com.fyp.crowdlink.data.ble

import android.content.SharedPreferences
import android.util.Log
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.NearbyFriend
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
    private val friendRepository: FriendRepository,
    private val messageRepository: MessageRepository,
    private val sharedPreferences: SharedPreferences,
    private val meshRoutingEngine: MeshRoutingEngine
) : DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices

    private val _nearbyFriends = MutableStateFlow<List<NearbyFriend>>(emptyList())
    val nearbyFriends: StateFlow<List<NearbyFriend>> = _nearbyFriends.asStateFlow()

    init {
        meshRoutingEngine.localDeviceId = sharedPreferences
            .getString("device_id", "") ?: ""
        
        // Wire MeshRoutingEngine callbacks
        meshRoutingEngine.onMessageForMe = { meshMessage ->
            scope.launch {
                val senderIdString = meshMessage.senderId.toString()
                val content = meshMessage.payload.toString(Charsets.UTF_8)
                
                val incomingMessage = Message(
                    messageId = meshMessage.messageId.toString(),
                    senderId = senderIdString,
                    receiverId = meshRoutingEngine.localDeviceId,
                    content = content,
                    timestamp = meshMessage.timestamp,
                    isSentByMe = false,
                    deliveryStatus = MessageStatus.DELIVERED,
                    hopCount = meshMessage.hopCount,
                    transportType = TransportType.MESH
                )
                
                messageRepository.sendMessage(incomingMessage)
                
                // UPDATE: Refresh last seen when a message is received
                friendRepository.updateLastSeen(senderIdString, meshMessage.timestamp)
                
                Log.d("MeshRouting", "Saved incoming message and updated lastSeen for $senderIdString")
            }
        }

        meshRoutingEngine.onRelay = { relayedMessage ->
            messageRepository.addToRelayQueue(relayedMessage)
        }

        bleScanner.observeRelayQueue(scope, messageRepository)

        // UPDATE: Sync real-time BLE discovery back to the database
        bleScanner.discoveredDevices
            .onEach { devices ->
                devices.forEach { device ->
                    scope.launch {
                        if (friendRepository.isFriendPaired(device.deviceId)) {
                            friendRepository.updateLastSeen(device.deviceId, device.lastSeen)
                        }
                    }
                }
            }
            .launchIn(scope)

        combine(
            bleScanner.discoveredDevices,
            friendRepository.getAllFriends()
        ) { rawDevices, friends ->
            val friendsMap = friends.associateBy { it.deviceId }
            rawDevices.mapNotNull { device ->
                val friend = friendsMap[device.deviceId]
                if (friend != null) {
                    NearbyFriend(
                        deviceId = friend.deviceId,
                        displayName = friend.displayName,
                        rssi = device.rssi,
                        estimatedDistance = device.estimatedDistance,
                        lastSeen = device.lastSeen
                    )
                } else null
            }
        }.onEach { result ->
            _nearbyFriends.value = result
        }.launchIn(scope)
    }

    override fun startDiscovery() = bleScanner.startDiscovery()
    override fun stopDiscovery() = bleScanner.stopDiscovery()
    override fun startAdvertising(myDeviceId: String) = bleAdvertiser.startAdvertising(myDeviceId)
    override fun stopAdvertising() = bleAdvertiser.stopAdvertising()
    override suspend fun getPairedFriends(): List<Friend> = friendRepository.getAllFriends().first()
}
