package com.fyp.crowdlink.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey val deviceId: String,  // Unique identifier
    val displayName: String,            // User-entered name
    val publicKey: String?,             // For future encryption (Week 8)
    val pairedAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = 0L
)