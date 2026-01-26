package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.StateFlow

/**
 * DeviceRepository
 *
 * This interface defines the contract for device discovery and Bluetooth Low Energy (BLE) operations.
 * It manages the scanning for nearby devices, advertising the local device's presence,
 * and exposing the list of discovered devices to the UI layer.
 */
interface DeviceRepository {
    
    /**
     * A StateFlow emitting the current list of discovered devices.
     * The list is updated in real-time as devices are found or updated during scanning.
     */
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>

    /**
     * Starts the BLE scanning process to find nearby devices.
     * Requires Bluetooth and Location permissions to be granted.
     */
    fun startDiscovery()

    /**
     * Stops the BLE scanning process to conserve battery.
     */
    fun stopDiscovery()

    /**
     * Starts advertising the local device's presence over BLE.
     * This allows other devices to discover and connect to this device.
     *
     * @param myDeviceId The unique ID of this device to broadcast in the BLE payload.
     */
    fun startAdvertising(myDeviceId: String)

    /**
     * Stops advertising the local device's presence.
     */
    fun stopAdvertising()

    /**
     * Retrieves the list of friends that have been paired with this device.
     * Useful for filtering discovered devices to show only known friends.
     *
     * @return A list of [Friend] objects representing paired contacts.
     */
    suspend fun getPairedFriends(): List<Friend>
}
