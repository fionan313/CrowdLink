package com.fyp.crowdlink.domain.usecase

import app.cash.turbine.test
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetMessagesUseCaseTest {

    private lateinit var useCase: GetMessagesUseCase
    private lateinit var mockMessageRepository: MessageRepository

    @Before
    fun setup() {
        mockMessageRepository = mockk(relaxed = true)
        useCase = GetMessagesUseCase(mockMessageRepository)
    }

    @Test
    fun `invoke returns messages flow from repository`() = runTest {
        val testMessages = listOf(
            Message(id = 1L, senderId = "ME", receiverId = "FRIEND1", content = "Hello", isSentByMe = true, transportType = TransportType.MESH),
            Message(id = 2L, senderId = "FRIEND1", receiverId = "ME", content = "Hi!", isSentByMe = false, transportType = TransportType.MESH)
        )
        every { mockMessageRepository.getMessagesWithFriend("FRIEND1") } returns flowOf(testMessages)

        useCase("FRIEND1").test {
            val messages = awaitItem()
            assertEquals(2, messages.size)
            assertEquals("Hello", messages[0].content)
            assertEquals("Hi!", messages[1].content)
            awaitComplete()
        }
    }

    @Test
    fun `invoke returns empty flow when no messages exist`() = runTest {
        every { mockMessageRepository.getMessagesWithFriend("NEW") } returns flowOf(emptyList())

        useCase("NEW").test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `invoke passes friendId to repository correctly`() = runTest {
        every { mockMessageRepository.getMessagesWithFriend(any()) } returns flowOf(emptyList())

        useCase("specific-id")

        verify { mockMessageRepository.getMessagesWithFriend("specific-id") }
    }
}
