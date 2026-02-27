package com.fyp.crowdlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.RelayMessageDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.data.local.entity.RelayMessageEntity
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.UserProfile

/**
 * AppDatabase
 *
 * This is the main Room database definition for the application.
 */
@Database(
    entities = [Friend::class, UserProfile::class, Message::class, RelayMessageEntity::class],
    version = 8, // Incremented to version 8 as version 7 was incomplete
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun friendDao(): FriendDao
    
    abstract fun userProfileDao(): UserProfileDao

    abstract fun messageDao(): MessageDao

    abstract fun relayMessageDao(): RelayMessageDao
}
