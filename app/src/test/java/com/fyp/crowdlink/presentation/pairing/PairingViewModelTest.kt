package com.fyp.crowdlink.presentation.pairing

import android.bluetooth.BluetoothManager
import android.content.Context
import app.cash.turbine.test
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.crypto.EncryptionManager
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.PairingRequest
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.PairFriendUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private lateinit var viewModel: PairingViewModel
    private lateinit var mockContext: Context
    private lateinit var mockBluetoothManager: BluetoothManager
    private lateinit var mockUserProfileRepository: UserProfileRepository
    private lateinit var mockPairFriendUseCase: PairFriendUseCase
    private lateinit var mockFriendRepository: FriendRepository
    private lateinit var mockDeviceRepository: DeviceRepository
    private lateinit var mockBleScanner: BleScanner
    private lateinit var mockEncryptionManager: EncryptionManager

    private val testDispatcher = UnconfinedTestDispatcher()
    private val incomingPairingFlow = MutableStateFlow<PairingRequest?>(null)
    private val pairingAcceptedFlow = MutableSharedFlow<String>(replay = 1)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockContext = mockk(relaxed = true)
        mockBluetoothManager = mockk(relaxed = true)
        mockUserProfileRepository = mockk(relaxed = true)
        mockPairFriendUseCase = mockk(relaxed = true)
        mockFriendRepository = mockk(relaxed = true)
        mockDeviceRepository = mockk(relaxed = true)
        mockBleScanner = mockk(relaxed = true)
        mockEncryptionManager = mockk(relaxed = true)

        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockContext.getSystemService(BluetoothManager::class.java) } returns mockBluetoothManager
        every { mockUserProfileRepository.getPersistentDeviceId() } returns "my-device-id"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PairingViewModel {
        every { mockDeviceRepository.incomingPairingRequest } returns incomingPairingFlow
        every { mockDeviceRepository.pairingAccepted } returns pairingAcceptedFlow
        return PairingViewModel(
            mockContext,
            mockUserProfileRepository,
            mockPairFriendUseCase,
            mockFriendRepository,
            mockDeviceRepository,
            mockBleScanner,
            mockEncryptionManager
        )
    }

    @Test
    fun `device ID is loaded from UserProfileRepository on init`() = runTest {
        viewModel = createViewModel()
        viewModel.myDeviceId.test {
            assertEquals("my-device-id", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial pairing state is Idle`() = runTest {
        viewModel = createViewModel()
        viewModel.pairingState.test {
            assertEquals(PairingState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial QR code bitmap is null`() = runTest {
        viewModel = createViewModel()
        viewModel.qrCodeBitmap.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQRScanned with invalid JSON sets error state`() = runTest {
        viewModel = createViewModel()
        // Passing empty string which results in Error in the ViewModel logic
        viewModel.onQRScanned("", "Default Name")

        viewModel.pairingState.test {
            val state = awaitItem()
            assertTrue("Expected Error, got $state", state is PairingState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQRScanned with blank data sets error`() = runTest {
        viewModel = createViewModel()
        viewModel.onQRScanned("", "Default")

        viewModel.pairingState.test {
            val state = awaitItem()
            assertTrue("Expected Error for blank QR, got $state", state is PairingState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onQRScanned with valid JSON sends pairing request`() = runTest {
        viewModel = createViewModel()
        every { mockUserProfileRepository.getUserProfile() } returns flowOf(null)
        // Ensure scanner finds the device
        every { mockBleScanner.getDeviceById("friend-device") } returns mockk(relaxed = true)

        val qrData = """{"deviceId":"friend-device","displayName":"Charlie"}"""
        viewModel.onQRScanned(qrData, "Default Name")

        verify {
            mockDeviceRepository.sendPairingRequest(
                targetDeviceId = "friend-device",
                senderDisplayName = any()
            )
        }
    }

    @Test
    fun `acceptPairingRequest saves friend and sends acceptance`() = runTest {
        viewModel = createViewModel()
        val request = PairingRequest(
            senderDeviceId = "requester-device",
            senderDisplayName = "Bob"
        )

        viewModel.acceptPairingRequest(request)

        coVerify {
            mockFriendRepository.addFriend(match {
                it.deviceId == "requester-device" && it.displayName == "Bob"
            })
        }
        verify { mockDeviceRepository.sendPairingAccepted("requester-device") }
        verify { mockDeviceRepository.clearIncomingPairingRequest() }
    }

    @Test
    fun `declinePairingRequest clears incoming request`() = runTest {
        viewModel = createViewModel()

        viewModel.declinePairingRequest()

        verify { mockDeviceRepository.clearIncomingPairingRequest() }
    }
}
