package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.PairingRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * DeviceRepository
 *
 * This interface defines the contract for device discovery and Bluetooth Low Energy (BLE) operations.
 */
interface DeviceRepository {
    
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    val isGattServerReady: StateFlow<Boolean>

    val incomingPairingRequest: StateFlow<PairingRequest?>

    val lastGattError: StateFlow<Pair<Int, Long>?>

    val pairingAccepted: SharedFlow<String>

    fun startDiscovery()

    fun stopDiscovery()

    fun startAdvertising(myDeviceId: String)

    fun stopAdvertising()

    suspend fun getPairedFriends(): List<Friend>

    /**
     * Sends a pairing request to a target device over BLE.
     */
    fun sendPairingRequest(targetDeviceId: String, senderDisplayName: String, sharedKey: String?)
    
    /**
     * Sends a pairing acceptance back to the requester.
     */
    fun sendPairingAccepted(targetDeviceId: String)

    /**
     * Notifies a target device that we have unpaired from them.
     */
    fun sendUnpairNotification(targetDeviceId: String)

    /**
     * Clears the current incoming pairing request.
     */
    fun clearIncomingPairingRequest()

    /**
     * Broadcasts an SOS alert to all currently paired friends via the mesh.
     * Includes last known GPS coordinates if available.
     */
    suspend fun sendSosAlert()
}
