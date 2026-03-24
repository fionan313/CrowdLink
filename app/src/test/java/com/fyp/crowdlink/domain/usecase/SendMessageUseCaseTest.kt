package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SendMessageUseCaseTest {

    private lateinit var useCase: SendMessageUseCase
    private lateinit var mockMessageRepository: MessageRepository

    @Before
    fun setup() {
        mockMessageRepository = mockk(relaxed = true)
        useCase = SendMessageUseCase(mockMessageRepository)
    }

    @Test
    fun `invoke delegates to repository and returns message ID`() = runTest {
        val message = Message(
            senderId = "ME",
            receiverId = "FRIEND",
            content = "Test message",
            isSentByMe = true,
            transportType = TransportType.MESH
        )
        coEvery { mockMessageRepository.sendMessage(message) } returns 42L

        val result = useCase(message)

        assertEquals(42L, result)
        coVerify(exactly = 1) { mockMessageRepository.sendMessage(message) }
    }

    @Test
    fun `invoke passes message unchanged to repository`() = runTest {
        val capturedMessage = slot<Message>()
        coEvery { mockMessageRepository.sendMessage(capture(capturedMessage)) } returns 1L

        val message = Message(
            senderId = "SENDER_A",
            receiverId = "RECEIVER_B",
            content = "Specific content",
            isSentByMe = true,
            deliveryStatus = MessageStatus.PENDING,
            transportType = TransportType.MESH
        )

        useCase(message)

        assertEquals("SENDER_A", capturedMessage.captured.senderId)
        assertEquals("RECEIVER_B", capturedMessage.captured.receiverId)
        assertEquals("Specific content", capturedMessage.captured.content)
        assertEquals(TransportType.MESH, capturedMessage.captured.transportType)
    }
}
