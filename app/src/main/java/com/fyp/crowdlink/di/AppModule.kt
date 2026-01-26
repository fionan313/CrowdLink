package com.fyp.crowdlink.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fyp.crowdlink.data.local.FriendDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.data.repository.FriendRepositoryImpl
import com.fyp.crowdlink.data.repository.MessageRepositoryImpl
import com.fyp.crowdlink.data.repository.UserProfileRepositoryImpl
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule
 *
 * This module provides application-level dependencies for dependency injection using Dagger Hilt.
 * It manages the creation and lifecycle of singletons such as the Database, DAOs, Repositories,
 * and SharedPreferences.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the singleton instance of the FriendDatabase.
     *
     * @param context The application context.
     * @return The Room database instance for storing friend and user profile data.
     */
    @Provides
    @Singleton
    fun provideFriendDatabase(@ApplicationContext context: Context): FriendDatabase {
        return Room.databaseBuilder(
            context,
            FriendDatabase::class.java,
            "friend_db"
        )
        .fallbackToDestructiveMigration() // Wipes database on version change to prevent crashes during development
        .build()
    }

    /**
     * Provides the FriendDao for accessing friend-related database operations.
     *
     * @param db The FriendDatabase instance.
     * @return The FriendDao implementation.
     */
    @Provides
    @Singleton
    fun provideFriendDao(db: FriendDatabase): FriendDao {
        return db.friendDao()
    }

    /**
     * Provides the UserProfileDao for accessing user profile database operations.
     *
     * @param db The FriendDatabase instance.
     * @return The UserProfileDao implementation.
     */
    @Provides
    @Singleton
    fun provideUserProfileDao(db: FriendDatabase): UserProfileDao {
        return db.userProfileDao()
    }

    /**
     * Provides the MessageDao for accessing message-related database operations.
     *
     * @param db The FriendDatabase instance.
     * @return The MessageDao implementation.
     */
    @Provides
    @Singleton
    fun provideMessageDao(db: FriendDatabase): MessageDao {
        return db.messageDao()
    }

    /**
     * Provides the FriendRepository implementation.
     *
     * @param friendDao The FriendDao dependency.
     * @return The concrete implementation of FriendRepository.
     */
    @Provides
    @Singleton
    fun provideFriendRepository(friendDao: FriendDao): FriendRepository {
        return FriendRepositoryImpl(friendDao)
    }

    /**
     * Provides the UserProfileRepository implementation.
     *
     * @param userProfileDao The UserProfileDao dependency.
     * @return The concrete implementation of UserProfileRepository.
     */
    @Provides
    @Singleton
    fun provideUserProfileRepository(userProfileDao: UserProfileDao): UserProfileRepository {
        return UserProfileRepositoryImpl(userProfileDao)
    }

    /**
     * Provides the MessageRepository implementation.
     *
     * @param messageDao The MessageDao dependency.
     * @return The concrete implementation of MessageRepository.
     */
    @Provides
    @Singleton
    fun provideMessageRepository(messageDao: MessageDao): MessageRepository {
        return MessageRepositoryImpl(messageDao)
    }
    
    /**
     * Provides the SharedPreferences instance for the application.
     *
     * @param context The application context.
     * @return The SharedPreferences instance named "crowdlink_prefs".
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("crowdlink_prefs", Context.MODE_PRIVATE)
    }
}
