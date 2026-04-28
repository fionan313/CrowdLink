package com.fyp.crowdlink.domain.usecase

import javax.inject.Inject
import kotlin.math.pow

/**
 * EstimateDistanceUseCase
 *
 * Estimates the physical distance to a nearby BLE device using the log-distance
 * path loss model. The result is used by the discovery screen to display approximate
 * distances to paired friends and by the compass screen as a fallback when GPS is unavailable.
 *
 * Formula: distance = 10 ^ ((TxPower - RSSI) / (10 * pathLossExponent))
 */
class EstimateDistanceUseCase @Inject constructor() {

    companion object {
        private const val TX_POWER = -59        // Calibrated RSSI at 1 metre reference distance
        private const val PATH_LOSS_EXPONENT = 2.5 // 2.0 = free space, 4.0 = dense obstacles
    }

    /**
     * Invokes the distance estimation for a given RSSI reading.
     *
     * @param rssi The received signal strength in dBm from a BLE scan result.
     * @return The estimated distance in metres, or -1.0 if the RSSI value is invalid.
     */
    operator fun invoke(rssi: Int): Double {
        return if (rssi == 0) {
            -1.0 // Invalid RSSI value
        } else {
            val ratio = (TX_POWER - rssi) / (10.0 * PATH_LOSS_EXPONENT)
            10.0.pow(ratio)
        }
    }
}