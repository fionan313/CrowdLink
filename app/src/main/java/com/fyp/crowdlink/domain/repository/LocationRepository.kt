package com.fyp.crowdlink.domain.repository

import com.fyp.crowdlink.domain.model.DeviceLocation
import kotlinx.coroutines.flow.Flow

/**
 * LocationRepository
 *
 * Interface for managing device and friend locations.
 */
interface LocationRepository {
    /**
     * Returns a Flow of the current device's location.
     */
    fun getMyLocation(): Flow<DeviceLocation?>

    /**
     * Retrieves the last known location of the current device.
     */
    suspend fun getLastKnownLocation(): DeviceLocation?

    /**
     * Caches a friend's location in the local database.
     */
    suspend fun cacheFriendLocation(location: DeviceLocation)

    /**
     * Returns a Flow of a specific friend's location by their device ID.
     */
    fun getFriendLocation(deviceId: String): Flow<DeviceLocation?>
}
