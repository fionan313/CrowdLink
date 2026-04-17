package com.fyp.crowdlink.di

import android.content.Context
import android.content.SharedPreferences
import android.hardware.SensorManager
import androidx.room.Room
import com.fyp.crowdlink.data.ble.BleAdvertiser
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.data.crypto.EncryptionManager
import com.fyp.crowdlink.data.local.AppDatabase
import com.fyp.crowdlink.data.local.dao.*
import com.fyp.crowdlink.data.mesh.LocationMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshMessageSerialiser
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.mesh.SeenMessageCache
import com.fyp.crowdlink.data.notifications.MeshNotificationManager
import com.fyp.crowdlink.data.repository.*
import com.fyp.crowdlink.domain.repository.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// @Binds is used here because LocationRepositoryImpl is injected by Hilt directly —
// no manual construction needed, unlike the repos in AppModule
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository
}

// provides all app-level singletons to the Hilt dependency graph
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // fallbackToDestructiveMigration — acceptable during development, replace with proper
    // migrations before any public release
    @Provides
    @Singleton
    fun provideFriendDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "friend_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    // DAO providers — each pulls from the single AppDatabase instance above
    @Provides @Singleton
    fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()

    @Provides @Singleton
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()

    @Provides @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides @Singleton
    fun provideRelayMessageDao(db: AppDatabase): RelayMessageDao = db.relayMessageDao()

    @Provides @Singleton
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    @Singleton
    fun provideFriendRepository(friendDao: FriendDao): FriendRepository {
        return FriendRepositoryImpl(friendDao)
    }

    @Provides
    @Singleton
    fun provideUserProfileRepository(
        userProfileDao: UserProfileDao,
        sharedPreferences: SharedPreferences
    ): UserProfileRepository {
        return UserProfileRepositoryImpl(userProfileDao, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideMessageRepository(
        messageDao: MessageDao,
        relayMessageDao: RelayMessageDao
    ): MessageRepository {
        return MessageRepositoryImpl(messageDao, relayMessageDao)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("crowdlink_prefs", Context.MODE_PRIVATE)
    }

    // localDeviceId is set here at construction time so the routing engine is ready
    // before any BLE traffic arrives
    @Provides
    @Singleton
    fun provideMeshRoutingEngine(
        cache: SeenMessageCache,
        userProfileRepository: UserProfileRepository,
        sharedPreferences: SharedPreferences
    ): MeshRoutingEngine {
        return MeshRoutingEngine(cache, sharedPreferences).apply {
            localDeviceId = userProfileRepository.getPersistentDeviceId()
        }
    }

    // manually constructed because DeviceRepositoryImpl wires BLE callbacks in init{} —
    // Hilt's @Binds approach can't guarantee the right construction order here
    @Provides
    @Singleton
    fun provideDeviceRepository(
        bleScanner: BleScanner,
        bleAdvertiser: BleAdvertiser,
        friendRepository: FriendRepository,
        messageRepository: MessageRepository,
        locationRepository: LocationRepository,
        sharedPreferences: SharedPreferences,
        meshRoutingEngine: MeshRoutingEngine,
        meshNotificationManager: MeshNotificationManager,
        locationSerialiser: LocationMessageSerialiser,
        userProfileRepository: UserProfileRepository,
        encryptionManager: EncryptionManager
    ): DeviceRepository {
        return DeviceRepositoryImpl(
            bleScanner,
            bleAdvertiser,
            friendRepository,
            messageRepository,
            locationRepository,
            sharedPreferences,
            meshRoutingEngine,
            meshNotificationManager,
            locationSerialiser,
            userProfileRepository,
            encryptionManager
        )
    }

    @Provides @Singleton
    fun provideMeshMessageSerializer(): MeshMessageSerialiser = MeshMessageSerialiser()

    @Provides @Singleton
    fun provideSeenMessageCache(): SeenMessageCache = SeenMessageCache()

    @Provides @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @Provides @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context
    ): SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
}