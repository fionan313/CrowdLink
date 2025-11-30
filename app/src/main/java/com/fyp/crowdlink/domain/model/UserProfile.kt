package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,  // Single row
    val displayName: String,
    val phoneNumber: String? = null,
    val statusMessage: String? = null,  // e.g., "At Electric Picnic 2025"
    val updatedAt: Long = System.currentTimeMillis()
)