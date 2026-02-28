package com.fyp.crowdlink.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fyp.crowdlink.data.local.AppDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.MessageDao
import com.fyp.crowdlink.data.local.dao.RelayMessageDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.mesh.SeenMessageCache
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
     * Provides the singleton instance of the AppDatabase.
     *
     * @param context The application context.
     * @return The Room database instance for storing friend and user profile data.
     */
    @Provides
    @Singleton
    fun provideFriendDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "friend_db"
        )
        .fallbackToDestructiveMigration() // Wipes database on version change to prevent crashes during development
        .build()
    }

    /**
     * Provides the FriendDao for accessing friend-related database operations.
     *
     * @param db The AppDatabase instance.
     * @return The FriendDao implementation.
     */
    @Provides
    @Singleton
    fun provideFriendDao(db: AppDatabase): FriendDao {
        return db.friendDao()
    }

    /**
     * Provides the UserProfileDao for accessing user profile database operations.
     *
     * @param db The AppDatabase instance.
     * @return The UserProfileDao implementation.
     */
    @Provides
    @Singleton
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao {
        return db.userProfileDao()
    }

    /**
     * Provides the MessageDao for accessing message-related database operations.
     *
     * @param db The AppDatabase instance.
     * @return The MessageDao implementation.
     */
    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao {
        return db.messageDao()
    }

    /**
     * Provides the RelayMessageDao for accessing relay message database operations.
     *
     * @param db The AppDatabase instance.
     * @return The RelayMessageDao implementation.
     */
    @Provides
    @Singleton
    fun provideRelayMessageDao(db: AppDatabase): RelayMessageDao {
        return db.relayMessageDao()
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
     * @param sharedPreferences The SharedPreferences dependency.
     * @return The concrete implementation of UserProfileRepository.
     */
    @Provides
    @Singleton
    fun provideUserProfileRepository(
        userProfileDao: UserProfileDao,
        sharedPreferences: SharedPreferences
    ): UserProfileRepository {
        return UserProfileRepositoryImpl(userProfileDao, sharedPreferences)
    }

    /**
     * Provides the MessageRepository implementation.
     *
     * @param messageDao The MessageDao dependency.
     * @param relayMessageDao The RelayMessageDao dependency.
     * @return The concrete implementation of MessageRepository.
     */
    @Provides
    @Singleton
    fun provideMessageRepository(
        messageDao: MessageDao,
        relayMessageDao: RelayMessageDao
    ): MessageRepository {
        return MessageRepositoryImpl(messageDao, relayMessageDao)
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

    @Provides
    @Singleton
    fun provideMeshRoutingEngine(
        cache: SeenMessageCache,
        userProfileRepository: UserProfileRepository
    ): MeshRoutingEngine {
        return MeshRoutingEngine(cache).apply {
            localDeviceId = userProfileRepository.getPersistentDeviceId()
        }
    }

    @Provides
    @Singleton
    fun provideMeshMessageSerializer(): MeshMessageSerialiser {
        return MeshMessageSerialiser()
    }
}
