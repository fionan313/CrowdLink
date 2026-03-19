package com.fyp.crowdlink.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.crowdlink.data.local.AppDatabase
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.TransportType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageRepositoryIntegrationTest {
    
    private lateinit var database: AppDatabase
    private lateinit var repository: MessageRepositoryImpl
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        
        repository = MessageRepositoryImpl(database.messageDao(), database.relayMessageDao())
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun sendMessage_messageIsSaved() = runBlocking {
        // Given
        val message = Message(
            senderId = "ME",
            receiverId = "FRIEND",
            content = "Hello!",
            isSentByMe = true,
            transportType = TransportType.MESH
        )
        
        // When
        val messageId = repository.sendMessage(message)
        val messages = repository.getMessagesWithFriend("FRIEND").first()
        
        // Then
        assertTrue(messageId > 0)
        assertEquals(1, messages.size)
        assertEquals("Hello!", messages[0].content)
    }
    
    @Test
    fun getMessagesWithFriend_returnsOnlyRelevantMessages() = runBlocking {
        // Given
        val message1 = Message(senderId = "ME", receiverId = "FRIEND1", content = "Msg to Friend 1", isSentByMe = true, transportType = TransportType.MESH)
        val message2 = Message(senderId = "ME", receiverId = "FRIEND2", content = "Msg to Friend 2", isSentByMe = true, transportType = TransportType.MESH)
        val message3 = Message(senderId = "FRIEND1", receiverId = "ME", content = "Reply from Friend 1", isSentByMe = false, transportType = TransportType.MESH)
        
        repository.sendMessage(message1)
        repository.sendMessage(message2)
        repository.sendMessage(message3)
        
        // When
        val friend1Messages = repository.getMessagesWithFriend("FRIEND1").first()
        
        // Then
        assertEquals(2, friend1Messages.size) // message1 and message3
    }
    
    @Test
    fun updateMessageStatus_statusIsUpdated() = runBlocking {
        // Given
        val message = Message(senderId = "ME", receiverId = "FRIEND", content = "Test", isSentByMe = true, transportType = TransportType.MESH)
        val messageId = repository.sendMessage(message)
        
        // When
        repository.updateMessageStatus(messageId, MessageStatus.DELIVERED)
        val messages = repository.getMessagesWithFriend("FRIEND").first()
        
        // Then
        assertEquals(MessageStatus.DELIVERED, messages[0].deliveryStatus)
    }
}
