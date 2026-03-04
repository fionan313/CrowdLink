package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.data.mesh.LocationMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
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
        val myLocation = locationRepository.getLastKnownLocation() ?: return
        
        val serialisedLocation = locationSerialiser.serialize(myLocation)
        
        val meshMessage = meshRoutingEngine.createOutbound(
            senderId = meshRoutingEngine.localDeviceId,
            recipientId = friendDeviceId,
            payload = serialisedLocation
        )
        
        messageRepository.addToRelayQueue(meshMessage)
    }
}
