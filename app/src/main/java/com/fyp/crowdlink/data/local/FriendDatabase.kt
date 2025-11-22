package com.fyp.crowdlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.domain.model.Friend

@Database(
    entities = [Friend::class],
    version = 1,
    exportSchema = false
)
abstract class FriendDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
}