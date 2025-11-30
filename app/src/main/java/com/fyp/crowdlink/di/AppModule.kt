package com.fyp.crowdlink.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.fyp.crowdlink.data.local.FriendDatabase
import com.fyp.crowdlink.data.local.dao.FriendDao
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.data.repository.FriendRepositoryImpl
import com.fyp.crowdlink.data.repository.UserProfileRepositoryImpl
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
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
    fun provideFriendDatabase(@ApplicationContext context: Context): FriendDatabase {
        return Room.databaseBuilder(
            context,
            FriendDatabase::class.java,
            "friend_db"
        )
        .fallbackToDestructiveMigration() // Wipes database on version change to prevent crashes during development
        .build()
    }

    @Provides
    @Singleton
    fun provideFriendDao(db: FriendDatabase): FriendDao {
        return db.friendDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(db: FriendDatabase): UserProfileDao {
        return db.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideFriendRepository(friendDao: FriendDao): FriendRepository {
        return FriendRepositoryImpl(friendDao)
    }

    @Provides
    @Singleton
    fun provideUserProfileRepository(userProfileDao: UserProfileDao): UserProfileRepository {
        return UserProfileRepositoryImpl(userProfileDao)
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("crowdlink_prefs", Context.MODE_PRIVATE)
    }
}