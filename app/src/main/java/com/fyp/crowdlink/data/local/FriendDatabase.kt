package com.fyp.crowdlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.UserProfile

/**
 * FriendDatabase
 *
 * This is the main Room database definition for the application.
 */
@Database(
    entities = [Friend::class, UserProfile::class, Message::class],
    version = 5, // Incremented for Message entity
    exportSchema = false
)
abstract class FriendDatabase : RoomDatabase() {
    
    abstract fun friendDao(): FriendDao
    
    abstract fun userProfileDao(): UserProfileDao

    abstract fun messageDao(): MessageDao
}
