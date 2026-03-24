package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.data.mesh.LocationMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * ShareLocationUseCase
 *
 * Use case for sharing the current device's location with a friend via the mesh network.
 */
class ShareLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val messageRepository: MessageRepository,
    private val meshRoutingEngine: MeshRoutingEngine,
    private val locationSerialiser: LocationMessageSerialiser
) {
    suspend operator fun invoke(friendDeviceId: String) {
        // Prefer a fresh fix with acceptable accuracy
        val myLocation = try {
            locationRepository.getMyLocation()
                .filter { it != null && it.accuracy < 50f }
                .first()
        } catch (_: Exception) {
            // Flow timed out or errored — fall back to last known
            locationRepository.getLastKnownLocation()
        } ?: return  // Nothing at all — bail out silently

        val serialisedLocation = locationSerialiser.serialize(myLocation)

        val meshMessage = meshRoutingEngine.createOutbound(
            senderId = meshRoutingEngine.localDeviceId,
            recipientId = friendDeviceId,
            payload = serialisedLocation
        )

        messageRepository.addToRelayQueue(meshMessage)
    }
}
