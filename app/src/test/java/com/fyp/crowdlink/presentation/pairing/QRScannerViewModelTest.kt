package com.fyp.crowdlink.presentation.pairing

import app.cash.turbine.test
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QRScannerViewModelTest {

    private lateinit var viewModel: QRScannerViewModel
    private lateinit var mockFriendRepository: FriendRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockFriendRepository = mockk(relaxed = true)
        viewModel = QRScannerViewModel(mockFriendRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial pairing status is Idle`() = runTest {
        viewModel.pairingStatus.test {
            assertEquals(PairingStatus.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid QR code creates friend and emits Success`() = runTest {
        // Given
        val qrPayload = """{"deviceId":"device-123","displayName":"Alice"}"""
        coEvery { mockFriendRepository.getFriendById("device-123") } returns null
        coEvery { mockFriendRepository.addFriend(any()) } just Runs

        // When
        viewModel.onQRCodeScanned(qrPayload)

        // Then
        viewModel.pairingStatus.test {
            val status = awaitItem()
            assertTrue("Expected Success, got $status", status is PairingStatus.Success)
            assertEquals("Alice", (status as PairingStatus.Success).friendName)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            mockFriendRepository.addFriend(match {
                it.deviceId == "device-123" && it.displayName == "Alice"
            })
        }
    }

    @Test
    fun `scanning already paired friend emits AlreadyPaired`() = runTest {
        // Given
        val existingFriend = Friend(
            deviceId = "device-123",
            displayName = "Alice",
            pairedAt = 1000L,
            lastSeen = 1000L
        )
        val qrPayload = """{"deviceId":"device-123","displayName":"Alice"}"""
        coEvery { mockFriendRepository.getFriendById("device-123") } returns existingFriend

        // When
        viewModel.onQRCodeScanned(qrPayload)

        // Then
        viewModel.pairingStatus.test {
            val status = awaitItem()
            assertTrue("Expected AlreadyPaired, got $status", status is PairingStatus.AlreadyPaired)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { mockFriendRepository.addFriend(any()) }
    }

    @Test
    fun `invalid JSON emits Error`() = runTest {
        // When
        viewModel.onQRCodeScanned("not-valid-json")

        // Then
        viewModel.pairingStatus.test {
            val status = awaitItem()
            assertTrue("Expected Error, got $status", status is PairingStatus.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty string emits Error`() = runTest {
        // When
        viewModel.onQRCodeScanned("")

        // Then
        viewModel.pairingStatus.test {
            val status = awaitItem()
            assertTrue("Expected Error, got $status", status is PairingStatus.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `QR code missing deviceId emits Error`() = runTest {
        // When
        viewModel.onQRCodeScanned("""{"displayName":"Alice"}""")

        // Then
        viewModel.pairingStatus.test {
            val status = awaitItem()
            assertTrue("Expected Error, got $status", status is PairingStatus.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
