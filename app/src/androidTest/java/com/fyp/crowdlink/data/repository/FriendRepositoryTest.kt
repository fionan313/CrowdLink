package com.fyp.crowdlink.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.crowdlink.data.local.FriendDatabase
import com.fyp.crowdlink.domain.model.Friend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FriendRepositoryIntegrationTest {
    
    private lateinit var database: FriendDatabase
    private lateinit var repository: FriendRepositoryImpl
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FriendDatabase::class.java
        ).allowMainThreadQueries().build()
        
        repository = FriendRepositoryImpl(database.friendDao())
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun addFriend_retrieveFriend_success() = runBlocking {
        // Given
        val friend = Friend(
            deviceId = "TEST123",
            displayName = "Test Friend",
            pairedAt = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis()
        )
        
        // When
        repository.addFriend(friend)
        val friends = repository.getAllFriends().first()
        
        // Then
        assertEquals(1, friends.size)
        assertEquals("TEST123", friends[0].deviceId)
        assertEquals("Test Friend", friends[0].displayName)
    }
    
    @Test
    fun addMultipleFriends_retrieveAll_success() = runBlocking {
        // Given
        val friend1 = Friend(deviceId = "ID1", displayName = "Friend 1", pairedAt = 1000L, lastSeen = 1000L)
        val friend2 = Friend(deviceId = "ID2", displayName = "Friend 2", pairedAt = 2000L, lastSeen = 2000L)
        val friend3 = Friend(deviceId = "ID3", displayName = "Friend 3", pairedAt = 3000L, lastSeen = 3000L)
        
        // When
        repository.addFriend(friend1)
        repository.addFriend(friend2)
        repository.addFriend(friend3)
        
        val friends = repository.getAllFriends().first()
        
        // Then
        assertEquals(3, friends.size)
    }
    
    @Test
    fun removeFriend_friendIsDeleted() = runBlocking {
        // Given
        val friend = Friend(deviceId = "ID1", displayName = "Friend", pairedAt = 1000L, lastSeen = 1000L)
        repository.addFriend(friend)
        
        // When
        repository.removeFriend(friend)
        val friends = repository.getAllFriends().first()
        
        // Then
        assertEquals(0, friends.size)
    }
    
    @Test
    fun getFriendById_returnsCorrectFriend() = runBlocking {
        // Given
        val friend = Friend(deviceId = "ID123", displayName = "Specific Friend", pairedAt = 1000L, lastSeen = 1000L)
        repository.addFriend(friend)
        
        // When
        val result = repository.getFriendById("ID123")
        
        // Then
        assertNotNull(result)
        assertEquals("Specific Friend", result?.displayName)
    }
    
    @Test
    fun isFriendPaired_returnsTrueForExistingFriend() = runBlocking {
        // Given
        val friend = Friend(deviceId = "ID456", displayName = "Paired Friend", pairedAt = 1000L, lastSeen = 1000L)
        repository.addFriend(friend)
        
        // When
        val isPaired = repository.isFriendPaired("ID456")
        
        // Then
        assertTrue(isPaired)
    }
    
    @Test
    fun isFriendPaired_returnsFalseForNonExistingFriend() = runBlocking {
        // When
        val isPaired = repository.isFriendPaired("NONEXISTENT")
        
        // Then
        assertFalse(isPaired)
    }
    
    @Test
    fun updateLastSeen_updatesTimestamp() = runBlocking {
        // Given
        val friend = Friend(deviceId = "ID789", displayName = "Friend", pairedAt = 1000L, lastSeen = 1000L)
        repository.addFriend(friend)
        
        val newTimestamp = 5000L
        
        // When
        repository.updateLastSeen("ID789", newTimestamp)
        val updatedFriend = repository.getFriendById("ID789")
        
        // Then
        assertEquals(newTimestamp, updatedFriend?.lastSeen)
    }
}
