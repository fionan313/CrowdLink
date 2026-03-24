package com.fyp.crowdlink.data.ble

import android.content.SharedPreferences
import com.fyp.crowdlink.data.mesh.LocationMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.notifications.MeshNotificationManager
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.NearbyFriend
import com.fyp.crowdlink.domain.model.PairingRequest
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
    private val friendRepository: FriendRepository,
    private val messageRepository: MessageRepository,
    private val locationRepository: LocationRepository,
    private val sharedPreferences: SharedPreferences,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val meshNotificationManager: MeshNotificationManager,
    private val locationSerialiser: LocationMessageSerialiser,
    private val userProfileRepository: UserProfileRepository
) : DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices

    private val _incomingPairingRequest = MutableStateFlow<PairingRequest?>(null)
    override val incomingPairingRequest: StateFlow<PairingRequest?> = _incomingPairingRequest.asStateFlow()

    private val _pairingAccepted = MutableSharedFlow<String>(replay = 0)
    override val pairingAccepted: SharedFlow<String> = _pairingAccepted.asSharedFlow()

    private val _nearbyFriends = MutableStateFlow<List<NearbyFriend>>(emptyList())
    val nearbyFriends: StateFlow<List<NearbyFriend>> = _nearbyFriends.asStateFlow()

    init {
        meshRoutingEngine.localDeviceId = sharedPreferences
            .getString("device_id", "") ?: ""
        
        // Wire MeshRoutingEngine callbacks
        meshRoutingEngine.onMessageForMe = { meshMessage, transportType ->
            scope.launch {
                val senderIdString = meshMessage.senderId
                val payload = meshMessage.payload
                
                if (payload.isNotEmpty()) {
                    when (payload[0]) {
                        0x01.toByte() -> handleIncomingTextMessage(senderIdString, meshMessage, transportType)
                        0x03.toByte() -> handleIncomingLocationUpdate(senderIdString, payload)
                        else -> Timber.tag("DeviceRepo").w("Unknown message type: ${payload[0]}")
                    }
                }
                
                // Refresh last seen when any message is received
                friendRepository.updateLastSeen(senderIdString, meshMessage.timestamp)
            }
        }

        meshRoutingEngine.onRelay = { relayedMessage ->
            messageRepository.addToRelayQueue(relayedMessage)
        }

        bleScanner.observeRelayQueue(scope, messageRepository)

        // Wire pairing request callbacks
        bleAdvertiser.onPairingRequestReceived = { request ->
            _incomingPairingRequest.value = request
        }

        bleAdvertiser.onPairingAcceptedReceived = { acceptedDeviceId ->
            scope.launch {
                Timber.tag("DeviceRepo").d("Pairing accepted received from $acceptedDeviceId")
                _pairingAccepted.emit(acceptedDeviceId)
            }
        }

        bleAdvertiser.onUnpairRequestReceived = { senderId ->
            scope.launch {
                friendRepository.removeFriendById(senderId)
                Timber.tag("DeviceRepo")
                    .d("Removed $senderId from friends list via unpair notification")
            }
        }

        bleAdvertiser.onSosAlertReceived = { senderId, senderName, latitude, longitude ->
            scope.launch {
                meshNotificationManager.showSosNotification(
                    senderName = senderName,
                    latitude = latitude,
                    longitude = longitude,
                    friendId = senderId
                )
            }
        }

        // Sync real-time BLE discovery back to the database
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

    private suspend fun handleIncomingTextMessage(
        senderId: String, 
        meshMessage: com.fyp.crowdlink.domain.model.MeshMessage,
        transportType: TransportType
    ) {
        val content = meshMessage.payload.toString(Charsets.UTF_8).substring(1) // Skip type byte
        val friend = friendRepository.getFriendById(senderId)
        
        // Logical display logic: if it was relayed, show "MESH". If direct, show physical transport.
        val displayTransport = if (meshMessage.hopCount > 0) TransportType.MESH else transportType

        val incomingMessage = Message(
            messageId = meshMessage.messageId.toString(),
            senderId = senderId,
            receiverId = meshRoutingEngine.localDeviceId,
            content = content,
            timestamp = meshMessage.timestamp,
            isSentByMe = false,
            deliveryStatus = MessageStatus.DELIVERED,
            hopCount = meshMessage.hopCount,
            transportType = displayTransport
        )
        
        messageRepository.sendMessage(incomingMessage)
        
        meshNotificationManager.showMessageNotification(
            senderName = friend?.displayName ?: "Unknown",
            content = content,
            friendId = senderId
        )
    }

    private suspend fun handleIncomingLocationUpdate(senderId: String, payload: ByteArray) {
        val location = locationSerialiser.deserialize(payload, senderId)
        if (location != null) {
            locationRepository.cacheFriendLocation(location)
            Timber.tag("DeviceRepo").d("Cached location update from $senderId")
        }
    }

    override fun startDiscovery() = bleScanner.startDiscovery()
    override fun stopDiscovery() = bleScanner.stopDiscovery()
    override fun startAdvertising(myDeviceId: String) = bleAdvertiser.startAdvertising(myDeviceId)
    override fun stopAdvertising() = bleAdvertiser.stopAdvertising()
    override suspend fun getPairedFriends(): List<Friend> = friendRepository.getAllFriends().first()

    override fun sendPairingRequest(targetDeviceId: String, senderDisplayName: String) {
        val device = bleScanner.getDeviceById(targetDeviceId)
        if (device == null) {
            Timber.tag("DeviceRepo")
                .e("Cannot send pairing request: target device $targetDeviceId not in range")
            return
        }

        val payload = JSONObject().apply {
            put("senderId", meshRoutingEngine.localDeviceId)
            put("senderName", senderDisplayName)
        }.toString().toByteArray(Charsets.UTF_8)

        val finalPayload = ByteArray(payload.size + 1)
        finalPayload[0] = BleAdvertiser.PAIRING_REQUEST_PREFIX
        System.arraycopy(payload, 0, finalPayload, 1, payload.size)

        bleScanner.sendData(finalPayload, device)
    }

    override fun sendPairingAccepted(targetDeviceId: String) {
        val device = bleScanner.getDeviceById(targetDeviceId) ?: return

        val payload = JSONObject().apply {
            put("senderId", meshRoutingEngine.localDeviceId)
        }.toString().toByteArray(Charsets.UTF_8)

        val finalPayload = ByteArray(payload.size + 1)
        finalPayload[0] = BleAdvertiser.PAIRING_ACCEPTED_PREFIX
        System.arraycopy(payload, 0, finalPayload, 1, payload.size)

        bleScanner.sendData(finalPayload, device)
    }

    override fun sendUnpairNotification(targetDeviceId: String) {
        val device = bleScanner.getDeviceById(targetDeviceId)
        if (device == null) {
            Timber.tag("DeviceRepo").w("Unpair target not in range — they'll clean up on next seen")
            return
        }

        val payload = JSONObject().apply {
            put("senderId", meshRoutingEngine.localDeviceId)
        }.toString().toByteArray(Charsets.UTF_8)

        val finalPayload = ByteArray(payload.size + 1)
        finalPayload[0] = BleAdvertiser.UNPAIR_REQUEST_PREFIX
        System.arraycopy(payload, 0, finalPayload, 1, payload.size)

        bleScanner.sendData(finalPayload, device)
    }

    override fun clearIncomingPairingRequest() {
        _incomingPairingRequest.value = null
    }

    override suspend fun sendSosAlert() {
        val friends = friendRepository.getAllFriends().first()
        if (friends.isEmpty()) return

        val myLocation = locationRepository.getLastKnownLocation()
        val myDisplayName = userProfileRepository.getUserProfile().first()?.displayName ?: "Unknown"

        val json = JSONObject().apply {
            put("senderId", meshRoutingEngine.localDeviceId)
            put("senderName", myDisplayName)
            myLocation?.let {
                put("lat", it.latitude)
                put("lon", it.longitude)
            }
        }.toString().toByteArray(Charsets.UTF_8)

        val payload = byteArrayOf(BleAdvertiser.SOS_ALERT_PREFIX) + json

        // Send to every known device in range
        bleScanner.getDiscoveredBluetoothDevices().forEach { device ->
            bleScanner.sendData(payload, device)
        }

        Timber.tag("DeviceRepo").d("SOS broadcast sent to ${friends.size} potential recipients")
    }
}
