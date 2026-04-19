package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DeviceLocation
import kotlinx.coroutines.flow.Flow

/**
 * LocationRepository
 *
 * Defines the contract for all location-related operations, covering both the local
 * device's live GPS position and cached friend locations received over the BLE mesh.
 * Friend locations are written on receipt and read by the compass and map screens.
 */
interface LocationRepository {

    /**
     * Returns a live stream of the current device's GPS position.
     * Emits a new value whenever the fused location provider delivers an update.
     *
     * @return A Flow emitting the device's current [DeviceLocation], or null if unavailable.
     */
    fun getMyLocation(): Flow<DeviceLocation?>

    /**
     * Returns the most recent cached GPS fix without requesting a fresh update.
     * Used as a fallback for SOS payloads and the initial map position.
     *
     * @return The last known [DeviceLocation], or null if no fix has been obtained.
     */
    suspend fun getLastKnownLocation(): DeviceLocation?

    /**
     * Writes a friend's location received over the mesh to the local database.
     * Replaces any existing record for that device.
     *
     * @param location The [DeviceLocation] to cache.
     */
    suspend fun cacheFriendLocation(location: DeviceLocation)

    /**
     * Returns a live stream of the last cached location for a specific friend.
     * Emits null if no location has been received from that device yet.
     *
     * @param deviceId The unique identifier of the friend whose location to observe.
     * @return A Flow emitting the friend's [DeviceLocation], or null if none is cached.
     */
    fun getFriendLocation(deviceId: String): Flow<DeviceLocation?>

    /**
     * Deletes all cached friend locations from the database.
     * Called on sign-out or when the user disables location sharing entirely.
     */
    suspend fun clearAllFriendLocations()

    /**
     * Deletes all locally cached offline map tiles from disk.
     * Called from the settings screen to free storage.
     */
    suspend fun clearMapCache()
}