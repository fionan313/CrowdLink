package com.fyp.crowdlink.presentation.settings

import android.content.SharedPreferences
import app.cash.turbine.test
import com.fyp.crowdlink.data.notifications.MeshNotificationManager
import com.fyp.crowdlink.domain.model.UserProfile
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var mockUserProfileRepository: UserProfileRepository
    private lateinit var mockFriendRepository: FriendRepository
    private lateinit var mockMessageRepository: MessageRepository
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockLocationRepository: LocationRepository
    private lateinit var mockNotificationManager: MeshNotificationManager

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testProfile = UserProfile(
        id = 1,
        displayName = "Fionán",
        phoneNumber = "0851234567",
        statusMessage = "At Electric Picnic"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockUserProfileRepository = mockk(relaxed = true)
        mockFriendRepository = mockk(relaxed = true)
        mockMessageRepository = mockk(relaxed = true)
        mockLocationRepository = mockk(relaxed = true)
        mockNotificationManager = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)

        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockSharedPreferences.getBoolean("auto_start", true) } returns true
        every { mockSharedPreferences.getBoolean("mesh_relay", true) } returns true
        every { mockSharedPreferences.getBoolean("esp32_scanning", false) } returns false
        every { mockSharedPreferences.getBoolean("ghost_mode", false) } returns false
        every { mockSharedPreferences.getBoolean("location_sharing", true) } returns true
        every { mockSharedPreferences.getBoolean("force_show_relays", false) } returns false
        every { mockUserProfileRepository.getPersistentDeviceId() } returns "test-device-id"
        every { mockFriendRepository.getAllFriends() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(profile: UserProfile? = testProfile): SettingsViewModel {
        coEvery { mockUserProfileRepository.getUserProfile() } returns flowOf(profile)
        return SettingsViewModel(
            mockUserProfileRepository,
            mockFriendRepository,
            mockMessageRepository,
            mockSharedPreferences,
            mockLocationRepository,
            mockNotificationManager
        )
    }

    @Test
    fun `existing profile is loaded on init`() = runTest {
        viewModel = createViewModel(testProfile)

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("Fionán", profile!!.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when no profile exists, defaults to Anonymous`() = runTest {
        viewModel = createViewModel(profile = null)

        viewModel.userProfile.test {
            val profile = awaitItem()
            assertEquals("Anonymous", profile!!.displayName)
            assertNull(profile.phoneNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial save status is Idle`() = runTest {
        viewModel = createViewModel()

        viewModel.saveStatus.test {
            assertEquals(SaveStatus.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveUserProfile calls repository with trimmed values`() = runTest {
        viewModel = createViewModel()

        viewModel.saveUserProfile("  Padded Name  ", "  085123  ", "  Status  ")

        coVerify {
            mockUserProfileRepository.saveUserProfile(match {
                it.displayName == "Padded Name" &&
                        it.phoneNumber == "085123" &&
                        it.statusMessage == "Status"
            })
        }
    }

    @Test
    fun `saveUserProfile converts empty optional fields to null`() = runTest {
        viewModel = createViewModel()

        viewModel.saveUserProfile("Name", "", "")

        coVerify {
            mockUserProfileRepository.saveUserProfile(match {
                it.displayName == "Name" &&
                        it.phoneNumber == null &&
                        it.statusMessage == null
            })
        }
    }

    @Test
    fun `saveUserProfile emits Error on repository failure`() = runTest {
        viewModel = createViewModel()
        coEvery { mockUserProfileRepository.saveUserProfile(any()) } throws RuntimeException("DB error")

        viewModel.saveUserProfile("Name", null, null)

        viewModel.saveStatus.test {
            val status = awaitItem()
            assertTrue("Expected Error, got $status", status is SaveStatus.Error)
            assertEquals("DB error", (status as SaveStatus.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMeshRelay persists to SharedPreferences`() = runTest {
        viewModel = createViewModel()

        viewModel.setMeshRelay(false)

        verify { mockEditor.putBoolean("mesh_relay", false) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `setLocationSharing clears locations when disabled`() = runTest {
        viewModel = createViewModel()

        viewModel.setLocationSharing(false)

        verify { mockEditor.putBoolean("location_sharing", false) }
        coVerify { mockLocationRepository.clearAllFriendLocations() }
    }

    @Test
    fun `clearMessageHistory delegates to message repository`() = runTest {
        viewModel = createViewModel()

        viewModel.clearMessageHistory()

        coVerify { mockMessageRepository.clearAllMessages() }
    }

    @Test
    fun `unpairAllFriends delegates to friend repository`() = runTest {
        viewModel = createViewModel()

        viewModel.unpairAllFriends()

        coVerify { mockFriendRepository.unpairAllFriends() }
    }

    @Test
    fun `resetAppData clears all data stores`() = runTest {
        viewModel = createViewModel()

        viewModel.resetAppData()

        coVerify { mockMessageRepository.clearAllMessages() }
        coVerify { mockLocationRepository.clearAllFriendLocations() }
        coVerify { mockLocationRepository.clearMapCache() }
        coVerify { mockFriendRepository.unpairAllFriends() }
        coVerify { mockUserProfileRepository.clearUserProfile() }
        verify { mockEditor.clear() }
    }
}
