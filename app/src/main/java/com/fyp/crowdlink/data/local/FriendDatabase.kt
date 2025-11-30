package com.fyp.crowdlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.UserProfile

@Database(
    entities = [Friend::class, UserProfile::class],
    version = 2,  // ← Increment version
    exportSchema = false
)
abstract class FriendDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun userProfileDao(): UserProfileDao
}