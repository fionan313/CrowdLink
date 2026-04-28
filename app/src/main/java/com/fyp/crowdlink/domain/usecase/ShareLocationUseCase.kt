package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.data.ble.BleAdvertiser
import com.fyp.crowdlink.data.crypto.EncryptionManager
import com.fyp.crowdlink.data.mesh.LocationMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * ShareLocationUseCase
 *
 * Serialises the current device's GPS position, encrypts it with the recipient's shared
 * key, and enqueues the resulting mesh packet for delivery over BLE. Called every 30 seconds
 * from the compass screen and every 60 seconds in the background from [MeshService].
 * If no shared key exists for the friend, a plaintext fallback is sent instead.
 */
class ShareLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val messageRepository: MessageRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val locationSerialiser: LocationMessageSerialiser,
    private val encryptionManager: EncryptionManager,
    private val friendRepository: FriendRepository
) {
    /**
     * Invokes the use case for a specific friend.
     *
     * @param friendDeviceId The device ID of the friend to share location with.
     */
    suspend operator fun invoke(friendDeviceId: String) {
        // prefer a fresh fix with acceptable accuracy, fall back to last known if unavailable
        val myLocation = try {
            locationRepository.getMyLocation()
                .filter { it != null && it.accuracy < 50f }
                .first()
        } catch (_: Exception) {
            locationRepository.getLastKnownLocation()
        } ?: return // no fix available at all - bail out silently

        val serialisedLocation = locationSerialiser.serialize(myLocation)

        val friend = friendRepository.getFriendById(friendDeviceId)

        val payload = if (friend?.sharedKey != null) {
            try {
                val ciphertext = encryptionManager.encrypt(serialisedLocation, friend.sharedKey)
                byteArrayOf(BleAdvertiser.ENCRYPTED_PAYLOAD_PREFIX) + ciphertext
            } catch (e: Exception) {
                Timber.tag("ShareLocationUseCase")
                    .e(e, "Encryption failed - sending plaintext fallback")
                serialisedLocation
            }
        } else {
            serialisedLocation
        }

        val meshMessage = meshRoutingEngine.createOutbound(
            senderId = meshRoutingEngine.localDeviceId,
            recipientId = friendDeviceId,
            payload = payload
        )

        messageRepository.addToRelayQueue(meshMessage)
    }
}