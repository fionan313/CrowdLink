package com.fyp.crowdlink.data.ble

import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import com.fyp.crowdlink.data.crypto.EncryptionManager
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
import kotlinx.coroutines.delay
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
    private val userProfileRepository: UserProfileRepository,
    private val encryptionManager: EncryptionManager
) : DeviceRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleScanner.discoveredDevices

    override val isGattServerReady: StateFlow<Boolean> = bleAdvertiser.isGattServerReady

    private val _incomingPairingRequest = MutableStateFlow<PairingRequest?>(null)
    override val incomingPairingRequest: StateFlow<PairingRequest?> = _incomingPairingRequest.asStateFlow()

    override val lastGattError: StateFlow<Pair<Int, Long>?> = bleScanner.lastGattError

    private val _pairingAccepted = MutableSharedFlow<String>(replay = 1)
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
                        BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX -> {
                            handleIncomingEncryptedMessage(senderIdString, meshMessage, transportType, payload.copyOfRange(1, payload.size))
                        }
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

        bleAdvertiser.onSosAlertReceived = { deviceAddress, rawPayload ->
            scope.launch {
                // Strip 0xFF prefix if encrypted
                val ciphertext = if (rawPayload.isNotEmpty() &&
                    rawPayload[0] == BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX) {
                    rawPayload.copyOfRange(1, rawPayload.size)
                } else {
                    rawPayload
                }

                // Find the friend whose key decrypts this payload
                val friends = friendRepository.getAllFriends().first()
                var senderId: String? = null
                var decryptedPayload: ByteArray? = null

                for (friend in friends) {
                    if (friend.sharedKey == null) continue
                    val attempt = try {
                        encryptionManager.decrypt(ciphertext, friend.sharedKey)
                    } catch (e: Exception) { null }
                    if (attempt != null) {
                        decryptedPayload = attempt
                        senderId = friend.deviceId
                        break
                    }
                }

                if (decryptedPayload == null || senderId == null) {
                    Timber.tag("DeviceRepo").w("SOS could not be decrypted — dropping")
                    return@launch
                }

                if (!friendRepository.isFriendPaired(senderId)) {
                    Timber.tag("DeviceRepo").w("Ignored SOS from unpaired device: $senderId")
                    return@launch
                }

                try {
                    val json = JSONObject(decryptedPayload.decodeToString(startIndex = 1))
                    val senderName = json.getString("senderName")
                    val latitude = if (json.has("lat")) json.getDouble("lat") else null
                    val longitude = if (json.has("lon")) json.getDouble("lon") else null

                    meshNotificationManager.showSosNotification(
                        senderName = senderName,
                        latitude = latitude,
                        longitude = longitude,
                        friendId = senderId
                    )
                    friendRepository.updateLastSeen(senderId, System.currentTimeMillis())
                } catch (e: Exception) {
                    Timber.tag("DeviceRepo").e(e, "Failed to parse SOS payload from $senderId")
                }
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

    private suspend fun handleIncomingEncryptedMessage(
        senderId: String,
        meshMessage: com.fyp.crowdlink.domain.model.MeshMessage,
        transportType: TransportType,
        ciphertext: ByteArray
    ) {
        val friend = friendRepository.getFriendById(senderId)

        if (friend?.sharedKey == null) {
            Timber.tag("DeviceRepo").w("No shared key for $senderId — cannot decrypt, dropping")
            return
        }

        val decryptedPayload = try {
            encryptionManager.decrypt(ciphertext, friend.sharedKey)
        } catch (e: Exception) {
            Timber.tag("DeviceRepo").e(e, "Decryption failed from $senderId — dropping")
            return
        }

        if (decryptedPayload.isNotEmpty()) {
            when (decryptedPayload[0]) {
                0x01.toByte() -> handleIncomingTextMessage(
                    senderId,
                    meshMessage.copy(payload = decryptedPayload),
                    transportType
                )
                0x03.toByte() -> handleIncomingLocationUpdate(senderId, decryptedPayload)
                else -> Timber.tag("DeviceRepo").w("Unknown decrypted type: ${decryptedPayload[0]}")
            }
        }
    }

    private suspend fun handleIncomingTextMessage(
        senderId: String, 
        meshMessage: com.fyp.crowdlink.domain.model.MeshMessage,
        transportType: TransportType
    ) {
        if (!friendRepository.isFriendPaired(senderId)) {
            Timber.tag("DeviceRepo").w("Ignored message from unpaired device: $senderId")
            return
        }

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
        if (!friendRepository.isFriendPaired(senderId)) {
            Timber.tag("DeviceRepo").w("Ignored location update from unpaired device: $senderId")
            return
        }

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

    override fun sendPairingRequest(targetDeviceId: String, senderDisplayName: String, sharedKey: String?) {
        val device = bleScanner.getDeviceById(targetDeviceId)
        if (device == null) {
            Timber.tag("DeviceRepo")
                .e("Cannot send pairing request: target device $targetDeviceId not in range")
            return
        }

        val payload = JSONObject().apply {
            put("senderId", meshRoutingEngine.localDeviceId)
            put("senderName", senderDisplayName)
            put("sharedKey", sharedKey ?: "")
        }.toString().toByteArray(Charsets.UTF_8)

        val finalPayload = ByteArray(payload.size + 1)
        finalPayload[0] = BleAdvertiser.PAIRING_REQUEST_PREFIX
        System.arraycopy(payload, 0, finalPayload, 1, payload.size)

        bleScanner.sendData(finalPayload, device)
    }

    override fun sendPairingAccepted(targetDeviceId: String) {
        scope.launch {
            var device: BluetoothDevice? = null
            repeat(5) { attempt ->
                device = bleScanner.getDeviceById(targetDeviceId)
                if (device != null) return@repeat
                Timber.tag("DeviceRepo").d("sendPairingAccepted: device not found, retry ${attempt + 1}/5")
                delay(1000)
            }
            if (device == null) {
                Timber.tag("DeviceRepo").e("sendPairingAccepted: $targetDeviceId not found after 5 retries")
                return@launch
            }
            val payload = JSONObject().apply {
                put("senderId", meshRoutingEngine.localDeviceId)
            }.toString().toByteArray(Charsets.UTF_8)
            val finalPayload = ByteArray(payload.size + 1)
            finalPayload[0] = BleAdvertiser.PAIRING_ACCEPTED_PREFIX
            System.arraycopy(payload, 0, finalPayload, 1, payload.size)
            bleScanner.sendData(finalPayload, device!!)
        }
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

        friends.forEach { friend ->
            val device = bleScanner.getDeviceById(friend.deviceId) ?: return@forEach

            val json = JSONObject().apply {
                put("senderId", meshRoutingEngine.localDeviceId)
                put("senderName", myDisplayName)
                myLocation?.let {
                    put("lat", it.latitude)
                    put("lon", it.longitude)
                }
            }.toString().toByteArray(Charsets.UTF_8)

            val plaintext = byteArrayOf(BleAdvertiser.SOS_ALERT_PREFIX) + json

            val payload = if (friend.sharedKey != null) {
                try {
                    val ciphertext = encryptionManager.encrypt(plaintext, friend.sharedKey)
                    byteArrayOf(BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX) + ciphertext
                } catch (e: Exception) {
                    Timber.tag("DeviceRepo").e(e, "SOS encryption failed for ${friend.deviceId}")
                    plaintext
                }
            } else {
                plaintext
            }

            bleScanner.sendData(payload, device)
        }

        Timber.tag("DeviceRepo").d("SOS sent to ${friends.size} paired friends")
    }
}
