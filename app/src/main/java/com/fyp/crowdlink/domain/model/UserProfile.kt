package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserProfile
 *
 * This Entity class represents the local user's own profile information stored in the Room database.
 * It is designed to hold a single row (id = 1) containing the user's display name, contact info,
 * and status, which can be shared with other users during pairing.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,        // Fixed ID to ensure only one profile exists (Singleton pattern in DB)
    val displayName: String,            // The name this user chooses to display to others
    val phoneNumber: String? = null,    // Optional phone number for contact
    val statusMessage: String? = null,  // A custom status message (e.g., "At Electric Picnic 2025")
    val updatedAt: Long = System.currentTimeMillis() // Timestamp of the last profile update
)
