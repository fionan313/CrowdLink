package com.fyp.crowdlink.domain.usecase

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EstimateDistanceUseCaseTest {

    private lateinit var useCase: EstimateDistanceUseCase

    @Before
    fun setup() {
        useCase = EstimateDistanceUseCase()
    }

    @Test
    fun `when RSSI is zero, returns unknown distance`() {
        // Given
        val rssi = 0

        // When
        val result = useCase(rssi)

        // Then
        assertEquals(-1.0, result, 0.01)
    }

    @Test
    fun `when RSSI equals TX_POWER, distance is approximately 1 meter`() {
        // Given - TX_POWER is -59 dBm
        val rssi = -59

        // When
        val result = useCase(rssi)

        // Then
        assertTrue("Distance should be 0.8-1.2m, was $result",
            result in 0.8..1.2)
    }

    @Test
    fun `when RSSI is weaker, distance increases`() {
        // Given
        val closeRssi = -60
        val farRssi = -80

        // When
        val closeDistance = useCase(closeRssi)
        val farDistance = useCase(farRssi)

        // Then
        assertTrue("Far distance ($farDistance) should be > close distance ($closeDistance)",
            farDistance > closeDistance)
    }

    @Test
    fun `when RSSI is -70 dBm, distance is within expected range`() {
        // Given
        val rssi = -70

        // When
        val result = useCase(rssi)

        // Then - Based on current algorithm: 10^((-59 - -70) / (10 * 2.5)) = 10^(11/25) = 10^0.44 ≈ 2.75m
        assertTrue("Distance should be positive, was $result", result > 0)
        assertEquals(2.75, result, 0.1)
    }

    @Test
    fun `when RSSI is -80 dBm, distance is within expected range`() {
        // Given
        val rssi = -80

        // When
        val result = useCase(rssi)

        // Then - Based on current algorithm: 10^((-59 - -80) / (10 * 2.5)) = 10^(21/25) = 10^0.84 ≈ 6.91m
        assertTrue("Distance should be positive, was $result", result > 0)
        assertEquals(6.91, result, 0.1)
    }

    @Test
    fun `when RSSI is -90 dBm, distance is within expected range`() {
        // Given
        val rssi = -90

        // When
        val result = useCase(rssi)

        // Then - Based on current algorithm: 10^((-59 - -90) / (10 * 2.5)) = 10^(31/25) = 10^1.24 ≈ 17.37m
        assertTrue("Distance should be positive, was $result", result > 0)
        assertEquals(17.37, result, 0.1)
    }

    @Test
    fun `distance never returns negative except for unknown`() {
        // Given
        val rssiValues = listOf(-50, -60, -70, -80, -90, -100)

        // When/Then
        rssiValues.forEach { rssi ->
            val result = useCase(rssi)
            assertTrue("Distance should be positive for RSSI $rssi, was $result",
                result > 0)
        }
    }

    @Test
    fun `algorithm is consistent for same RSSI`() {
        // Given
        val rssi = -75

        // When
        val result1 = useCase(rssi)
        val result2 = useCase(rssi)

        // Then
        assertEquals(result1, result2, 0.001)
    }
}
