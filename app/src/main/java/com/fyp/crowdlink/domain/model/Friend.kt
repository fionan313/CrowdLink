package com.fyp.crowdlink.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "public_key")
    val publicKey: String? = null,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = 0L,

    @ColumnInfo(name = "is_paired")
    val isPaired: Boolean = false
)