package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val deviceId: String,
    val shortId: String? = null, // Nullable for migration if needed, or default to take(16)
    val displayName: String,
    val nickname: String? = null,  // Optional nickname user gives them
    val phoneNumber: String? = null,  // Optional for emergencies
    val publicKey: String? = null,  // For Week 8 encryption
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = 0L
)