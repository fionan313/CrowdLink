package com.fyp.crowdlink.presentation.chat

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import app.cash.turbine.test
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.GetMessagesUseCase
import com.fyp.crowdlink.domain.usecase.SendMessageUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModelTest {

    private lateinit var viewModel: MessageViewModel
    private lateinit var mockWifiDirectManager: WifiDirectManager
    private lateinit var mockBleScanner: BleScanner
    private lateinit var mockSendMessageUseCase: SendMessageUseCase
    private lateinit var mockGetMessagesUseCase: GetMessagesUseCase
    private lateinit var mockUserProfileRepository: UserProfileRepository
    private lateinit var mockMeshRoutingEngine: MeshRoutingEngine
    private lateinit var mockMessageRepository: MessageRepository

    private val testDispatcher = UnconfinedTestDispatcher()
    private val peersFlow = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    private val connectionInfoFlow = MutableStateFlow<WifiP2pInfo?>(null)
    private val discoveredDevicesFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockWifiDirectManager = mockk(relaxed = true)
        mockBleScanner = mockk(relaxed = true)
        mockSendMessageUseCase = mockk(relaxed = true)
        mockGetMessagesUseCase = mockk(relaxed = true)
        mockUserProfileRepository = mockk(relaxed = true)
        mockMeshRoutingEngine = mockk(relaxed = true)
        mockMessageRepository = mockk(relaxed = true)

        every { mockWifiDirectManager.peers } returns peersFlow
        every { mockWifiDirectManager.connectionInfo } returns connectionInfoFlow
        every { mockBleScanner.discoveredDevices } returns discoveredDevicesFlow
        every { mockUserProfileRepository.getPersistentDeviceId() } returns "my-device-id"

        viewModel = MessageViewModel(
            mockWifiDirectManager,
            mockBleScanner,
            mockSendMessageUseCase,
            mockGetMessagesUseCase,
            mockUserProfileRepository,
            mockMeshRoutingEngine,
            mockMessageRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `device ID is loaded from UserProfileRepository on init`() = runTest {
        viewModel.myDeviceId.test {
            assertEquals("my-device-id", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mesh routing engine localDeviceId is set on init`() {
        verify { mockMeshRoutingEngine.localDeviceId = "my-device-id" }
    }

    @Test
    fun `onResume registers WiFi Direct and starts BLE discovery`() {
        viewModel.onResume()

        verify { mockWifiDirectManager.register() }
        verify { mockBleScanner.startDiscovery() }
    }

    @Test
    fun `onPause unregisters WiFi Direct and stops BLE discovery`() {
        viewModel.onPause()

        verify { mockWifiDirectManager.unregister() }
        verify { mockBleScanner.stopDiscovery() }
    }

    @Test
    fun `discover triggers both WiFi Direct and BLE discovery`() {
        viewModel.discover()

        verify { mockWifiDirectManager.discoverPeers() }
        verify { mockBleScanner.startDiscovery() }
    }

    @Test
    fun `getMessages returns flow from use case`() = runTest {
        // Given
        val testMessages = listOf(
            Message(
                id = 1L,
                senderId = "my-device-id",
                receiverId = "FRIEND1",
                content = "Hello",
                isSentByMe = true,
                transportType = TransportType.MESH
            )
        )
        every { mockGetMessagesUseCase("FRIEND1") } returns flowOf(testMessages)

        // When
        val messagesFlow = viewModel.getMessages("FRIEND1")

        // Then
        messagesFlow.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hello", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendText saves message locally and creates outbound mesh message`() = runTest {
        // Given
        coEvery { mockSendMessageUseCase(any()) } returns 1L
        val mockMeshMessage = MeshMessage(
            messageId = UUID.randomUUID(),
            senderId = "my-device-id",
            recipientId = "FRIEND1",
            payload = byteArrayOf(0x01) + "Hello mesh!".toByteArray()
        )
        every {
            mockMeshRoutingEngine.createOutbound("my-device-id", "FRIEND1", any())
        } returns mockMeshMessage

        // When
        viewModel.sendText("Hello mesh!", "FRIEND1")

        // Then — message saved to local DB
        coVerify {
            mockSendMessageUseCase(match {
                it.content == "Hello mesh!" &&
                        it.senderId == "my-device-id" &&
                        it.receiverId == "FRIEND1" &&
                        it.isSentByMe &&
                        it.transportType == TransportType.MESH
            })
        }

        // Then — mesh message created and added to relay queue
        verify { mockMeshRoutingEngine.createOutbound("my-device-id", "FRIEND1", any()) }
        coVerify { mockMessageRepository.addToRelayQueue(mockMeshMessage) }
    }

    @Test
    fun `connect finds peer by address and connects`() {
        val device = WifiP2pDevice().apply { deviceAddress = "AA:BB:CC:DD:EE:FF" }
        peersFlow.value = listOf(device)
        viewModel.connect("AA:BB:CC:DD:EE:FF")
        verify { mockWifiDirectManager.connect(device) }
    }

    @Test
    fun `connect does nothing when peer not found`() {
        // Given
        peersFlow.value = emptyList()

        // When
        viewModel.connect("AA:BB:CC:DD:EE:FF")

        // Then
        verify(exactly = 0) { mockWifiDirectManager.connect(any()) }
    }
}
