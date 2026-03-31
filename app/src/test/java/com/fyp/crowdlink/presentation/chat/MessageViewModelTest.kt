package com.fyp.crowdlink.presentation.chat

import android.content.SharedPreferences
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import app.cash.turbine.test
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.crypto.EncryptionManager
import com.fyp.crowdlink.data.mesh.MeshRoutingEngine
import com.fyp.crowdlink.data.p2p.WifiDirectManager
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import com.fyp.crowdlink.domain.model.Message
import com.fyp.crowdlink.domain.model.MeshMessage
import com.fyp.crowdlink.domain.model.MessageStatus
import com.fyp.crowdlink.domain.model.TransportType
import com.fyp.crowdlink.domain.repository.FriendRepository
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
    private lateinit var mockFriendRepository: FriendRepository
    private lateinit var mockEncryptionManager: EncryptionManager
    private lateinit var mockSharedPreferences: SharedPreferences

    private val testDispatcher = UnconfinedTestDispatcher()
    private val discoveredFriendsFlow = MutableStateFlow<Map<String, WifiP2pDevice>>(emptyMap())
    private val connectionInfoFlow = MutableStateFlow<WifiP2pInfo?>(null)
    private val discoveredDevicesFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    private val peerIpFlow = MutableStateFlow<String?>(null)

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
        mockFriendRepository = mockk(relaxed = true)
        mockEncryptionManager = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)

        every { mockWifiDirectManager.discoveredFriends } returns discoveredFriendsFlow
        every { mockWifiDirectManager.connectionInfo } returns connectionInfoFlow
        every { mockWifiDirectManager.peerIp } returns peerIpFlow
        every { mockBleScanner.discoveredDevices } returns discoveredDevicesFlow
        every { mockUserProfileRepository.getPersistentDeviceId() } returns "my-device-id"
        every { mockSharedPreferences.getBoolean("wifi_direct_mode", false) } returns false

        viewModel = MessageViewModel(
            mockWifiDirectManager,
            mockBleScanner,
            mockSendMessageUseCase,
            mockGetMessagesUseCase,
            mockUserProfileRepository,
            mockMeshRoutingEngine,
            mockMessageRepository,
            mockFriendRepository,
            mockEncryptionManager,
            mockSharedPreferences
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
    fun `onResume registers WiFi Direct, sets up discovery and starts BLE discovery`() {
        viewModel.onResume("FRIEND1")

        verify { mockWifiDirectManager.register() }
        verify { mockWifiDirectManager.setupServiceDiscovery("my-device-id") }
        verify { mockBleScanner.startDiscovery() }
        verify { mockMessageRepository.setActiveChatFriend("FRIEND1") }
    }

    @Test
    fun `onPause unregisters WiFi Direct, stops BLE discovery and clears active friend`() {
        viewModel.onPause()

        verify { mockWifiDirectManager.unregister() }
        verify { mockBleScanner.stopDiscovery() }
        verify { mockMessageRepository.setActiveChatFriend(null) }
    }

    @Test
    fun `discover triggers both WiFi Direct service and BLE discovery`() {
        viewModel.discover()

        verify { mockWifiDirectManager.discoverServices() }
        verify { mockBleScanner.startDiscovery() }
    }

    @Test
    fun `onResume triggers auto-connect when friend is discovered via WiFi Direct`() = runTest {
        // Given
        val friendDevice = mockk<WifiP2pDevice>()
        
        // When
        viewModel.onResume("FRIEND1")
        discoveredFriendsFlow.value = mapOf("FRIEND1" to friendDevice)
        
        // Then
        verify { mockWifiDirectManager.connect(friendDevice) }
    }

    @Test
    fun `getMessages returns flow from use case`() = runTest {
        // Given
        val testMessages = listOf(
            Message(
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
                it.receiverId == "FRIEND1" &&
                it.senderId == "my-device-id" &&
                it.isSentByMe &&
                (it.transportType == TransportType.BLE || it.transportType == TransportType.MESH)
            })
        }

        // Then — mesh message created and added to relay queue
        verify { mockMeshRoutingEngine.createOutbound("my-device-id", "FRIEND1", any()) }
        coVerify { mockMessageRepository.addToRelayQueue(mockMeshMessage) }
    }

    @Test
    fun `sendText uses WiFi Direct delivery when forced via debug setting`() = runTest {
        // Given
        every { mockSharedPreferences.getBoolean("wifi_direct_mode", false) } returns true
        peerIpFlow.value = "192.168.49.1"
        
        coEvery { mockSendMessageUseCase(any()) } returns 123L
        val mockMeshMessage = MeshMessage(
            messageId = UUID.randomUUID(),
            senderId = "my-device-id",
            recipientId = "FRIEND1",
            payload = byteArrayOf(0x01) + "Force WiFi".toByteArray()
        )
        every {
            mockMeshRoutingEngine.createOutbound("my-device-id", "FRIEND1", any())
        } returns mockMeshMessage
        coEvery { mockWifiDirectManager.deliverMeshMessage(any(), any()) } returns true

        // When
        viewModel.sendText("Force WiFi", "FRIEND1")

        // Then
        coVerify { mockWifiDirectManager.deliverMeshMessage("192.168.49.1", mockMeshMessage) }
        coVerify { mockMessageRepository.updateMessageStatus(123L, MessageStatus.SENT) }
        // Should NOT be added to relay queue
        coVerify(exactly = 0) { mockMessageRepository.addToRelayQueue(any()) }
    }
}
