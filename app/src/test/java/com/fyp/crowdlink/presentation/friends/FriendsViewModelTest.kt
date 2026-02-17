package com.fyp.crowdlink.presentation.friends

import app.cash.turbine.test
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private lateinit var viewModel: FriendsViewModel
    private lateinit var mockRepository: FriendRepository

    private val testDispatcher = UnconfinedTestDispatcher() // CHANGED: Use UnconfinedTestDispatcher

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)

        // Set up the mock BEFORE creating ViewModel
        val testFriends = listOf(
            Friend(
                deviceId = "ID1",
                displayName = "Friend 1",
                pairedAt = 1000L,
                lastSeen = 1000L
            ),
            Friend(
                deviceId = "ID2",
                displayName = "Friend 2",
                pairedAt = 2000L,
                lastSeen = 2000L
            )
        )
        coEvery { mockRepository.getAllFriends() } returns flowOf(testFriends)

        // Create the ViewModel
        viewModel = FriendsViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `friends flow emits data from repository`() = runTest {
        // When/Then
        viewModel.friends.test {
            val friends = awaitItem()
            assertEquals(2, friends.size)
            assertEquals("Friend 1", friends[0].displayName)
            assertEquals("Friend 2", friends[1].displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unpairFriend calls repository removeFriend`() = runTest {
        // Given
        val friend = Friend(
            deviceId = "ID123",
            displayName = "Friend",
            pairedAt = 1000L,
            lastSeen = 1000L
        )

        // When
        viewModel.unpairFriend(friend)

        // Then
        coVerify { mockRepository.removeFriend(friend) }
    }
}