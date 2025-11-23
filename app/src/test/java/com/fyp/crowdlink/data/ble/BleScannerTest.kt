package com.fyp.crowdlink.data.ble

import org.junit.Assert.*
import org.junit.Test

class BleScannerTest {

    @Test
    fun `when history is empty, smoothed RSSI is 0`() {
        // Given
        val history = emptyList<Int>()

        // When
        val result = BleScanner.calculateSmoothedRssi(history)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `when history has one value, returns that value`() {
        // Given
        val history = listOf(-65)

        // When
        val result = BleScanner.calculateSmoothedRssi(history)

        // Then
        assertEquals(-65, result)
    }

    @Test
    fun `when history has stable values, returns average`() {
        // Given - Stable signal around -65 dBm
        val history = listOf(-65, -64, -66, -65, -65, -64, -66, -65, -65, -64)

        // When
        val result = BleScanner.calculateSmoothedRssi(history)

        // Then - Average is -64.9
        assertEquals(-65, result)
    }

    @Test
    fun `smoothing handles noisy signal`() {
        // Given - Very noisy signal
        val history = listOf(-60, -80, -65, -70, -55, -75, -65, -68, -62, -72)

        // When
        val smoothed = BleScanner.calculateSmoothedRssi(history)

        // Then
        assertTrue("Smoothed should be between -80 and -55, was $smoothed",
            smoothed in -80..-55)
    }

    @Test
    fun `with 10 samples, calculates correct average`() {
        // Given
        val history = listOf(-70, -71, -69, -70, -72, -68, -70, -71, -69, -70)

        // When
        val result = BleScanner.calculateSmoothedRssi(history)

        // Then
        val expectedAverage = history.average().toInt()
        assertEquals(expectedAverage, result)
    }

    @Test
    fun `smoothing handles extreme RSSI values`() {
        // Given - Mix of very strong and very weak
        val history = listOf(-40, -95, -50, -90, -45, -95, -40, -92, -48, -94)

        // When
        val result = BleScanner.calculateSmoothedRssi(history)

        // Then
        assertTrue("Smoothed should be between -95 and -40, was $result",
            result in -95..-40)
    }
}