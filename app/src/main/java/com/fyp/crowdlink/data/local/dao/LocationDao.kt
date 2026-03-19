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
 * Data Access Object for location-related database operations.
 */
@Dao
interface LocationDao {
    /**
     * Inserts or updates a location in the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(entity: LocationEntity)

    /**
     * Retrieves the location for a specific device as a Flow.
     */
    @Query("SELECT * FROM locations WHERE deviceId = :deviceId")
    fun getLocationForDevice(deviceId: String): Flow<LocationEntity?>

    /**
     * Deletes all cached locations.
     */
    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}
