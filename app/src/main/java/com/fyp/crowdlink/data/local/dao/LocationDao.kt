package com.fyp.crowdlink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fyp.crowdlink.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * LocationDao
 *
 * Data Access Object (DAO) for the [LocationEntity] entity.
 * This interface defines the database interactions for managing cached friend locations.
 * Locations are written whenever a friend broadcasts their GPS coordinates over the mesh,
 * and read by the compass and map screens to display their last known position.
 */
@Dao
interface LocationDao {

    /**
     * Inserts a location record for a device, or replaces it if one already exists.
     * Each device only ever holds a single cached location — the most recently received.
     *
     * @param entity The [LocationEntity] containing the device ID, coordinates, and timestamp.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(entity: LocationEntity)

    /**
     * Retrieves the last known location for a specific device as a live stream.
     * Emits a new value whenever the location is updated, allowing the compass
     * and map screens to react without manual polling.
     *
     * @param deviceId The unique identifier of the friend whose location to retrieve.
     * @return A Flow emitting the [LocationEntity] for that device, or null if none is cached.
     */
    @Query("SELECT * FROM locations WHERE deviceId = :deviceId")
    fun getLocationForDevice(deviceId: String): Flow<LocationEntity?>

    /**
     * Deletes all cached locations from the database.
     * Called on sign-out or when the user disables location sharing entirely.
     */
    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}