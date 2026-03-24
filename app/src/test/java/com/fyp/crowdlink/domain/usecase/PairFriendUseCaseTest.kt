package com.fyp.crowdlink.domain.usecase

import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PairFriendUseCaseTest {

    private lateinit var useCase: PairFriendUseCase
    private lateinit var mockFriendRepository: FriendRepository

    @Before
    fun setup() {
        mockFriendRepository = mockk(relaxed = true)
        useCase = PairFriendUseCase(mockFriendRepository)
    }

    @Test
    fun `invoke creates friend with correct deviceId and displayName`() = runTest {
        coEvery { mockFriendRepository.addFriend(any()) } just Runs

        useCase("device-abc-123", "Alice")

        coVerify {
            mockFriendRepository.addFriend(match {
                it.deviceId == "device-abc-123" && it.displayName == "Alice"
            })
        }
    }

    @Test
    fun `invoke creates friend with null publicKey`() = runTest {
        val capturedFriend = slot<Friend>()
        coEvery { mockFriendRepository.addFriend(capture(capturedFriend)) } just Runs

        useCase("device-123", "Bob")

        assertNull(capturedFriend.captured.publicKey)
    }

    @Test
    fun `invoke delegates to repository exactly once`() = runTest {
        useCase("device-123", "Dave")

        coVerify(exactly = 1) { mockFriendRepository.addFriend(any()) }
    }
}
