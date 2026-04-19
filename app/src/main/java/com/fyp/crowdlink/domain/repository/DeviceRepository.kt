package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.PairingRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * DeviceRepository
 *
 * Defines the contract for BLE device discovery, advertising, and the pairing lifecycle.
 * Acts as the bridge between the presentation layer and the underlying BLE subsystem,
 * exposing reactive state flows for discovered devices and pairing events so the UI
 * can observe changes without polling.
 */
interface DeviceRepository {

    /**
     * Live list of nearby CrowdLink devices detected during scanning.
     * Updated continuously as new scan results arrive or existing entries expire.
     */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    /**
     * Emits true once the GATT server has fully registered its service.
     * Other components should wait on this before attempting GATT writes.
     */
    val isGattServerReady: StateFlow<Boolean>

    /**
     * Holds the most recent incoming pairing request, or null if none is pending.
     * Observed by the pairing screen to show the confirmation dialogue.
     */
    val incomingPairingRequest: StateFlow<PairingRequest?>

    /**
     * Emits the most recent GATT error code and the timestamp it occurred.
     * Used to trigger retry logic and backoff in the pairing flow.
     */
    val lastGattError: StateFlow<Pair<Int, Long>?>

    /**
     * Emits the device ID of a friend who has accepted a pairing request.
     * SharedFlow so the event is not replayed to late subscribers.
     */
    val pairingAccepted: SharedFlow<String>

    /**
     * Starts BLE scanning for nearby CrowdLink devices.
     */
    fun startDiscovery()

    /**
     * Stops an active BLE scan.
     */
    fun stopDiscovery()

    /**
     * Begins BLE advertising so nearby devices can discover and connect to this device.
     *
     * @param myDeviceId The local device ID to embed in the advertisement payload.
     */
    fun startAdvertising(myDeviceId: String)

    /**
     * Stops BLE advertising and closes the GATT server.
     */
    fun stopAdvertising()

    /**
     * Returns all currently paired friends from the local database.
     *
     * @return A list of [Friend] objects representing all paired devices.
     */
    suspend fun getPairedFriends(): List<Friend>

    /**
     * Sends a pairing request to a target device over BLE GATT.
     *
     * @param targetDeviceId The device ID of the friend to pair with.
     * @param senderDisplayName The display name to show in the recipient's confirmation dialogue.
     * @param sharedKey The AES-256-GCM shared key extracted from the QR code, or null if unavailable.
     */
    fun sendPairingRequest(targetDeviceId: String, senderDisplayName: String, sharedKey: String?)

    /**
     * Sends a pairing acceptance back to the device that initiated the request.
     *
     * @param targetDeviceId The device ID of the device that sent the original pairing request.
     */
    fun sendPairingAccepted(targetDeviceId: String)

    /**
     * Notifies a target device over BLE that this device has unpaired from them,
     * allowing the remote device to remove the corresponding friend record from its database.
     *
     * @param targetDeviceId The device ID of the friend being unpaired.
     */
    fun sendUnpairNotification(targetDeviceId: String)

    /**
     * Clears the current pending incoming pairing request.
     * Called after the user accepts or declines the confirmation dialogue.
     */
    fun clearIncomingPairingRequest()

    /**
     * Broadcasts an SOS alert to all currently discoverable paired friends via BLE.
     * Includes the sender's last known GPS coordinates if a fix is available.
     */
    suspend fun sendSosAlert()
}