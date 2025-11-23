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
    fun `when RSSI is -70 dBm, distance is 5-8 meters`() {
        // Given
        val rssi = -70

        // When
        val result = useCase(rssi)

        // Then
        assertTrue("Distance should be 5-8m, was $result",
            result in 5.0..8.0)
    }

    @Test
    fun `when RSSI is -80 dBm, distance is 15-20 meters`() {
        // Given
        val rssi = -80

        // When
        val result = useCase(rssi)

        // Then
        assertTrue("Distance should be 15-20m, was $result",
            result in 15.0..20.0)
    }

    @Test
    fun `when RSSI is -90 dBm, distance is 40-60 meters`() {
        // Given
        val rssi = -90

        // When
        val result = useCase(rssi)

        // Then
        assertTrue("Distance should be 40-60m, was $result",
            result in 40.0..60.0)
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