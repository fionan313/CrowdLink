package com.fyp.crowdlink.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.data.repository.FriendRepositoryImpl
import com.fyp.crowdlink.data.repository.UserProfileRepositoryImpl
import com.fyp.crowdlink.data.local.FriendDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFriendDatabase(
        @ApplicationContext context: Context
    ): FriendDatabase {
        return Room.databaseBuilder(
            context,
            FriendDatabase::class.java,
            "crowdlink_database"
        )
            .fallbackToDestructiveMigration() // ← For development only!
            .build()
    }

    @Provides
    fun provideFriendDao(database: FriendDatabase): FriendDao {
        return database.friendDao()
    }

    @Provides
    fun provideUserProfileDao(database: FriendDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(
            "crowdlink_prefs",
            Context.MODE_PRIVATE
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        impl: DeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        impl: FriendRepositoryImpl
    ): FriendRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        impl: UserProfileRepositoryImpl
    ): UserProfileRepository
}