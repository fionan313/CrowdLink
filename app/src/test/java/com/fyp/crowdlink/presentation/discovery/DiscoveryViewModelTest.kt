package com.fyp.crowdlink.presentation.discovery

import app.cash.turbine.test
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.domain.model.DiscoveredDevice
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    private lateinit var viewModel: DiscoveryViewModel
    private lateinit var mockRepository: DeviceRepositoryImpl
    private lateinit var devicesFlow: MutableStateFlow<List<DiscoveredDevice>>

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockRepository = mockk(relaxed = true)
        devicesFlow = MutableStateFlow(emptyList())

        every { mockRepository.discoveredDevices } returns devicesFlow

        viewModel = DiscoveryViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when ViewModel initialized, discovered devices flow is empty`() = runTest {
        viewModel.discoveredDevices.test {
            assertEquals(emptyList<DiscoveredDevice>(), awaitItem())
        }
    }

    @Test
    fun `when startDiscovery called, repository startDiscovery is invoked`() {
        // When
        viewModel.startDiscovery()

        // Then
        verify { mockRepository.startDiscovery() }
    }

    @Test
    fun `when stopDiscovery called, repository stopDiscovery is invoked`() {
        // When
        viewModel.stopDiscovery()

        // Then
        verify { mockRepository.stopDiscovery() }
    }

    @Test
    fun `when startAdvertising called, repository startAdvertising is invoked`() {
        // When
        viewModel.startAdvertising()

        // Then
        verify { mockRepository.startAdvertising() }
    }

    @Test
    fun `when stopAdvertising called, repository stopAdvertising is invoked`() {
        // When
        viewModel.stopAdvertising()

        // Then
        verify { mockRepository.stopAdvertising() }
    }

    @Test
    fun `when repository emits devices, ViewModel flow updates`() = runTest {
        // Given
        val device1 = DiscoveredDevice("AA:BB:CC:DD:EE:FF", -70, 8.5)
        val device2 = DiscoveredDevice("11:22:33:44:55:66", -65, 6.2)

        // When/Then
        viewModel.discoveredDevices.test {
            assertEquals(emptyList<DiscoveredDevice>(), awaitItem())

            devicesFlow.value = listOf(device1)
            assertEquals(listOf(device1), awaitItem())

            devicesFlow.value = listOf(device1, device2)
            assertEquals(listOf(device1, device2), awaitItem())
        }
    }

    @Test
    fun `when ViewModel cleared, stopDiscovery and stopAdvertising called`() {
        // When
        viewModel.onCleared()

        // Then
        verify { mockRepository.stopDiscovery() }
        verify { mockRepository.stopAdvertising() }
    }
}