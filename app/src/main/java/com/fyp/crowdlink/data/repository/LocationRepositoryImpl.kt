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

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationDao: LocationDao
) : LocationRepository {

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

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): DeviceLocation? {
        return try {
            fusedLocationClient.lastLocation.await()?.toDomain("me")
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun cacheFriendLocation(location: DeviceLocation) {
        locationDao.upsertLocation(location.toEntity())
    }

    override fun getFriendLocation(deviceId: String): Flow<DeviceLocation?> {
        return locationDao.getLocationForDevice(deviceId).map { it?.toDomain() }
    }

    override suspend fun clearAllFriendLocations() {
        locationDao.deleteAllLocations()
    }

    override suspend fun clearMapCache() {
        val cacheDir = File(context.filesDir, "map_tiles")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    private fun Location.toDomain(deviceId: String): DeviceLocation {
        return DeviceLocation(
            deviceId = deviceId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = time
        )
    }

    private fun LocationEntity.toDomain(): DeviceLocation {
        return DeviceLocation(
            deviceId = deviceId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = timestamp
        )
    }

    private fun DeviceLocation.toEntity(): LocationEntity {
        return LocationEntity(
            deviceId = deviceId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = timestamp
        )
    }
}
