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

/**
 * DeviceRepositoryImpl
 *
 * This class implements the [DeviceRepository] interface and serves as the central point for managing
 * device discovery. It orchestrates the [BleScanner] and [BleAdvertiser] and combines the raw
 * scan results with the user's list of paired friends from the [FriendRepository].
 *
 * The primary output is the [nearbyFriends] flow, which emits a list of [NearbyFriend] objects,
 * representing discovered devices that are also confirmed friends, enriched with display names.
 */
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

    // Exposes the raw, unfiltered list of discovered devices directly from the scanner.
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices

    // A new StateFlow that will hold the list of discovered devices that are confirmed friends.
    private val _nearbyFriends = MutableStateFlow<List<NearbyFriend>>(emptyList())
    val nearbyFriends: StateFlow<List<NearbyFriend>> = _nearbyFriends.asStateFlow()

    init {
        // SET THIS FIRST — must happen before any messages are processed
        meshRoutingEngine.localDeviceId = sharedPreferences
            .getString("device_id", "") ?: ""
        
        Log.d("MeshRouting", "localDeviceId set to: ${meshRoutingEngine.localDeviceId}")

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
                Log.d("MeshRouting", "Saved incoming message from $senderIdString")
            }
        }

        meshRoutingEngine.onRelay = { relayedMessage ->
            messageRepository.addToRelayQueue(relayedMessage)
            Log.d("MeshRouting", "Added relayed message ${relayedMessage.messageId} to relay queue")
        }

        // wire relay queue to BLE transport
        bleScanner.observeRelayQueue(scope, messageRepository)

        // Create a reactive pipeline that triggers whenever new devices are scanned OR the friends list changes.
        combine(
            bleScanner.discoveredDevices, // Flow of raw BLE scan results
            friendRepository.getAllFriends()    // Flow of friends from the database
        ) { rawDevices, friends ->
            Log.d("DeviceRepositoryImpl", "Combining ${rawDevices.size} devices with ${friends.size} friends.")

            // For efficient lookup, create a map of Friend's Device ID -> Friend object.
            val friendsMap = friends.associateBy { it.deviceId }

            // Iterate through the raw scanned devices and filter for those that are in our friends map.
            val nearbyFriendsList = rawDevices.mapNotNull { device ->
                // Check if the scanned device's ID exists in our map of friends.
                val friend = friendsMap[device.deviceId]

                if (friend != null) {
                    Log.d("DeviceRepositoryImpl", "✓ Matched friend: ${friend.displayName}")
                    // If a match is found, create a richer NearbyFriend object.
                    NearbyFriend(
                        deviceId = friend.deviceId,
                        displayName = friend.displayName,
                        rssi = device.rssi,
                        estimatedDistance = device.estimatedDistance,
                        lastSeen = device.lastSeen
                    )
                } else {
                    // If no match, this device is not a paired friend, so we ignore it.
                    null
                }
            }
            nearbyFriendsList
        }.onEach { result ->
            // Emit the newly combined list to the _nearbyFriends StateFlow.
            _nearbyFriends.value = result
        }.launchIn(scope) // Use the existing scope
    }

    /**
     * Starts the BLE scanning process by delegating to the BleScanner.
     */
    override fun startDiscovery() {
        bleScanner.startDiscovery()
    }

    /**
     * Starts BLE advertising by delegating to the BleAdvertiser.
     */
    override fun stopDiscovery() {
        bleScanner.stopDiscovery()
    }

    /**
     * Starts BLE advertising by delegating to the BleAdvertiser.
     */
    override fun startAdvertising(myDeviceId: String) {
        bleAdvertiser.startAdvertising(myDeviceId)
    }

    /**
     * Stops BLE advertising by delegating to the BleAdvertiser.
     */
    override fun stopAdvertising() {
        bleAdvertiser.stopAdvertising()
    }

    /**
     * Retrieves the list of all paired friends as a one-shot operation.
     */
    override suspend fun getPairedFriends(): List<Friend> {
        return friendRepository.getAllFriends().first()
    }
}
