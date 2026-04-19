package com.fyp.crowdlink.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.fyp.crowdlink.data.local.dao.LocationDao
import com.fyp.crowdlink.data.local.entity.LocationEntity
import com.fyp.crowdlink.domain.model.DeviceLocation
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationRepositoryImpl
 *
 * Handles all location concerns - live GPS updates from the fused location provider,
 * best-effort last known fixes, friend location caching from mesh payloads, and
 * offline map tile cache management.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationDao: LocationDao
) : LocationRepository {

    /**
     * Emits live GPS updates via a [callbackFlow]. The location callback is
     * automatically removed when the collector cancels, preventing leaks.
     */
    @SuppressLint("MissingPermission")
    override fun getMyLocation(): Flow<DeviceLocation?> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(location.toDomain("me"))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * Returns the last known fix from the fused provider without requesting a fresh update.
     * Used for the SOS payload and as a fallback when a live fix is unavailable.
     */
    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): DeviceLocation? {
        return try {
            fusedLocationClient.lastLocation.await()?.toDomain("me")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes a friend's location received over the mesh to Room.
     * The map and compass screens observe this via [getFriendLocation].
     */
    override suspend fun cacheFriendLocation(location: DeviceLocation) {
        locationDao.upsertLocation(location.toEntity())
    }

    /**
     * Returns a live stream of the last cached location for a specific friend.
     * Emits null if no location has been received yet.
     */
    override fun getFriendLocation(deviceId: String): Flow<DeviceLocation?> {
        return locationDao.getLocationForDevice(deviceId).map { it?.toDomain() }
    }

    override suspend fun clearAllFriendLocations() {
        locationDao.deleteAllLocations()
    }

    /**
     * Deletes the offline map tile cache from disk.
     * Called from the settings screen to free storage.
     */
    override suspend fun clearMapCache() {
        val cacheDir = File(context.filesDir, "map_tiles")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    // conversion helpers - keep mapping logic out of the domain layer

    private fun Location.toDomain(deviceId: String) = DeviceLocation(
        deviceId = deviceId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = time
    )

    private fun LocationEntity.toDomain() = DeviceLocation(
        deviceId = deviceId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp
    )

    private fun DeviceLocation.toEntity() = LocationEntity(
        deviceId = deviceId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp
    )
}