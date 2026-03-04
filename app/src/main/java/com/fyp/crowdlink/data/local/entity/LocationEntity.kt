package com.fyp.crowdlink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocationEntity
 *
 * Database entity for storing device locations.
 */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
