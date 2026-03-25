package com.fyp.crowdlink.presentation.sos

import app.cash.turbine.test
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosViewModelTest {

    private lateinit var viewModel: SosViewModel
    private lateinit var mockDeviceRepository: DeviceRepository
    private lateinit var mockFriendRepository: FriendRepository
    private lateinit var mockLocationRepository: LocationRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockDeviceRepository = mockk(relaxed = true)
        mockFriendRepository = mockk(relaxed = true)
        mockLocationRepository = mockk(relaxed = true)
        
        viewModel = SosViewModel(
            mockDeviceRepository,
            mockFriendRepository,
            mockLocationRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial sosSent is false`() = runTest {
        viewModel.sosSent.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial isSending is false`() = runTest {
        viewModel.isSending.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendSos calls deviceRepository and sets sosSent to true`() = runTest {
        // When
        viewModel.sendSos()

        // Then
        coVerify { mockDeviceRepository.sendSosAlert() }

        viewModel.sosSent.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendSos sets isSending false after completion`() = runTest {
        // When
        viewModel.sendSos()

        // Then
        viewModel.isSending.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
