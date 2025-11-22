package com.fyp.crowdlink.domain.usecase

import javax.inject.Inject
import kotlin.math.pow

/**
 * Use case for estimating distance from RSSI using the log-distance path loss model.
 *
 * Formula: distance = 10 ^ ((TxPower - RSSI) / (10 * pathLossExponent))
 */
class EstimateDistanceUseCase @Inject constructor() {

    companion object {
        private const val TX_POWER = -59  // Calibrated RSSI at 1 meter
        private const val PATH_LOSS_EXPONENT = 2.5  // 2.0 = free space, 4.0 = dense obstacles
    }

    operator fun invoke(rssi: Int): Double {
        return if (rssi == 0) {
            -1.0 // Unknown distance
        } else {
            val ratio = (TX_POWER - rssi) / (10.0 * PATH_LOSS_EXPONENT)
            10.0.pow(ratio)
        }
    }
}