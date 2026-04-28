package com.fyp.crowdlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.LocationDao
import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.RelayMessageDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.data.local.entity.LocationEntity
import com.fyp.crowdlink.data.local.entity.RelayMessageEntity
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.UserProfile

/**
 * AppDatabase
 *
 * The main Room database for CrowdLink. Holds all local state: paired friends,
 * the user's own profile, chat messages, the mesh relay queue, and cached friend locations.
 * Instantiated as a singleton by Hilt via [DatabaseModule].
 */
@Database(
    entities = [
        Friend::class,          // paired friends list
        UserProfile::class,     // local device identity
        Message::class,         // chat history
        RelayMessageEntity::class, // store-and-forward mesh queue
        LocationEntity::class   // last known friend locations
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun friendDao(): FriendDao

    abstract fun userProfileDao(): UserProfileDao

    abstract fun messageDao(): MessageDao

    abstract fun relayMessageDao(): RelayMessageDao

    abstract fun locationDao(): LocationDao
}