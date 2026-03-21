package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Friend
 *
 * This Entity class represents a friend in the local Room database.
 * It stores persistent information about paired contacts, including their
 * unique device ID, display name, and pairing metadata.
 */
@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val deviceId: String,   // Unique identifier for the friend's device (BLE Address/UUID)
    val shortId: String? = null,        // Shortened ID for display/verification (e.g. first 16 chars)
    val displayName: String,            // The public name shared by the friend during pairing
    val nickname: String? = null,       // An optional private nickname assigned by the user
    val phoneNumber: String? = null,    // Optional phone number for emergency contact
    val sharedKey: String? = null,      // AES-256-GCM symmetric key from pairing
    val pairedAt: Long = System.currentTimeMillis(), // Timestamp of when the pairing occurred
    val lastSeen : Long = 0L             // Timestamp of when this friend was last detected nearby
)
