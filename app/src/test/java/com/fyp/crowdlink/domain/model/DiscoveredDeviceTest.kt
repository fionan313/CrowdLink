package com.fyp.crowdlink.domain.model

import org.junit.Assert.*
import org.junit.Test

class DiscoveredDeviceTest {

    @Test
    fun `DiscoveredDevice holds correct values`() {
        // Given
        val deviceId = "AA:BB:CC:DD:EE:FF"
        val rssi = -70
        val timestamp = System.currentTimeMillis()
        val distance = 8.5
        val lastSeen = System.currentTimeMillis()

        // When
        val device = DiscoveredDevice(
            deviceId = deviceId,
            rssi = rssi,
            timestamp = timestamp,
            estimatedDistance = distance,
            lastSeen = lastSeen
        )

        // Then
        assertEquals(deviceId, device.deviceId)
        assertEquals(rssi, device.rssi)
        assertEquals(distance, device.estimatedDistance, 0.01)
        assertEquals(timestamp, device.timestamp)
        assertEquals(lastSeen, device.lastSeen)
    }

    @Test
    fun `devices with identical properties are equal`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val device1 = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            timestamp = timestamp,
            estimatedDistance = 8.5,
            lastSeen = timestamp
        )
        val device2 = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            timestamp = timestamp,
            estimatedDistance = 8.5,
            lastSeen = timestamp
        )

        // When/Then
        assertEquals(device1, device2)
    }

    @Test
    fun `devices with different properties are not equal`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val device1 = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            timestamp = timestamp,
            estimatedDistance = 8.5,
            lastSeen = timestamp
        )
        val device2 = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = -75,
            timestamp = timestamp,
            estimatedDistance = 12.0,
            lastSeen = timestamp
        )

        // When/Then
        assertNotEquals(device1, device2)
    }

    @Test
    fun `device can have negative distance for unknown`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val device = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = 0,
            timestamp = timestamp,
            estimatedDistance = -1.0,
            lastSeen = timestamp
        )

        // When/Then
        assertEquals(-1.0, device.estimatedDistance, 0.01)
    }

    @Test
    fun `estimatedDistance defaults to -1 when not provided`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val device = DiscoveredDevice(
            deviceId = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            lastSeen = timestamp
        )

        // When/Then
        assertEquals(-1.0, device.estimatedDistance, 0.01)
    }
}