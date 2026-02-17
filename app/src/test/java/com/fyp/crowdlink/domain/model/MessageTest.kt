package com.fyp.crowdlink.domain.model

import org.junit.Assert.*
import org.junit.Test

class MessageTest {

    @Test
    fun `Message creates with correct values`() {
        val message = Message(
            id = 1L,
            senderId = "SENDER_123",
            receiverId = "RECEIVER_456",
            content = "Hello, World!",
            timestamp = 1234567890L,
            deliveryStatus = MessageStatus.SENT,
            isSentByMe = true
        )

        assertEquals(1L, message.id)
        assertEquals("SENDER_123", message.senderId)
        assertEquals("RECEIVER_456", message.receiverId)
        assertEquals("Hello, World!", message.content)
        assertEquals(MessageStatus.SENT, message.deliveryStatus)
        assertTrue(message.isSentByMe)
    }

    @Test
    fun `Message defaults to PENDING status`() {
        val message = Message(
            senderId = "SENDER",
            receiverId = "RECEIVER",
            content = "Test",
            isSentByMe = true
        )

        assertEquals(MessageStatus.PENDING, message.deliveryStatus)
    }

    @Test
    fun `Sent and received messages are distinguished`() {
        val sentMessage = Message(
            senderId = "ME",
            receiverId = "FRIEND",
            content = "Sent",
            isSentByMe = true
        )

        val receivedMessage = Message(
            senderId = "FRIEND",
            receiverId = "ME",
            content = "Received",
            isSentByMe = false
        )

        assertTrue(sentMessage.isSentByMe)
        assertFalse(receivedMessage.isSentByMe)
    }

    @Test
    fun `Message status transitions correctly`() {
        val message = Message(
            senderId = "ME",
            receiverId = "FRIEND",
            content = "Test",
            isSentByMe = true,
            deliveryStatus = MessageStatus.PENDING
        )

        val sentMessage = message.copy(deliveryStatus = MessageStatus.SENT)
        assertEquals(MessageStatus.SENT, sentMessage.deliveryStatus)

        val deliveredMessage = sentMessage.copy(deliveryStatus = MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, deliveredMessage.deliveryStatus)
    }
}
