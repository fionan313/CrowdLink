package com.fyp.crowdlink.domain.model

import org.junit.Assert.*
import org.junit.Test

class DiscoveredDeviceTest {

    @Test
    fun `DiscoveredDevice holds correct values`() {
        // Given
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val rssi = -70
        val distance = 8.5

        // When
        val device = DiscoveredDevice(deviceId, rssi, distance)

        // Then
        assertEquals(deviceId, device.deviceId)
        assertEquals(rssi, device.rssi)
        assertEquals(distance, device.estimatedDistance, 0.01)
    }

    @Test
    fun `devices with identical properties are equal`() {
        // Given
        val device1 = DiscoveredDevice("AA:BB:CC:DD:EE:FF", -70, 8.5)
        val device2 = DiscoveredDevice("AA:BB:CC:DD:EE:FF", -70, 8.5)

        // When/Then
        assertEquals(device1, device2)
    }

    @Test
    fun `devices with different properties are not equal`() {
        // Given
        val device1 = DiscoveredDevice("AA:BB:CC:DD:EE:FF", -70, 8.5)
        val device2 = DiscoveredDevice("AA:BB:CC:DD:EE:FF", -75, 12.0)

        // When/Then
        assertNotEquals(device1, device2)
    }

    @Test
    fun `device can have negative distance for unknown`() {
        // Given
        val device = DiscoveredDevice("AA:BB:CC:DD:EE:FF", 0, -1.0)

        // When/Then
        assertEquals(-1.0, device.estimatedDistance, 0.01)
    }
}