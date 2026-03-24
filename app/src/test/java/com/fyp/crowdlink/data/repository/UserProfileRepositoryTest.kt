package com.fyp.crowdlink.data.repository

import android.content.SharedPreferences
import app.cash.turbine.test
import com.fyp.crowdlink.data.local.dao.UserProfileDao
import com.fyp.crowdlink.domain.model.UserProfile
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserProfileRepositoryTest {

    private lateinit var repository: UserProfileRepositoryImpl
    private lateinit var mockDao: UserProfileDao
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        
        repository = UserProfileRepositoryImpl(mockDao, mockSharedPreferences)
    }

    @Test
    fun `getUserProfile delegates to dao`() = runTest {
        val testProfile = UserProfile(displayName = "Test User", phoneNumber = "085123")
        every { mockDao.getUserProfile() } returns flowOf(testProfile)

        repository.getUserProfile().test {
            val profile = awaitItem()
            assertNotNull(profile)
            assertEquals("Test User", profile!!.displayName)
            awaitComplete()
        }
    }

    @Test
    fun `getUserProfile returns null when no profile`() = runTest {
        every { mockDao.getUserProfile() } returns flowOf(null)

        repository.getUserProfile().test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `getUserProfileOnce delegates to dao`() = runTest {
        val testProfile = UserProfile(displayName = "User")
        coEvery { mockDao.getUserProfileOnce() } returns testProfile

        val result = repository.getUserProfileOnce()

        assertEquals("User", result!!.displayName)
    }

    @Test
    fun `saveUserProfile delegates to dao`() = runTest {
        val profile = UserProfile(displayName = "New User")

        repository.saveUserProfile(profile)

        coVerify { mockDao.insertUserProfile(profile) }
    }

    @Test
    fun `updateUserProfile delegates to dao`() = runTest {
        val profile = UserProfile(displayName = "Updated")

        repository.updateUserProfile(profile)

        coVerify { mockDao.updateUserProfile(profile) }
    }

    @Test
    fun `deleteUserProfile delegates to dao`() = runTest {
        repository.deleteUserProfile()

        coVerify { mockDao.deleteUserProfile() }
    }

    @Test
    fun `getPersistentDeviceId returns existing ID if present`() {
        val existingId = "existing-uuid"
        every { mockSharedPreferences.getString("device_id", null) } returns existingId

        val result = repository.getPersistentDeviceId()

        assertEquals(existingId, result)
        verify(exactly = 0) { mockEditor.putString(any(), any()) }
    }

    @Test
    fun `getPersistentDeviceId creates and saves new ID if absent`() {
        every { mockSharedPreferences.getString("device_id", null) } returns null

        val result = repository.getPersistentDeviceId()

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        verify { mockEditor.putString("device_id", result) }
    }
}
