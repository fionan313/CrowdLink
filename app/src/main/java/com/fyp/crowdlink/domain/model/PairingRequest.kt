package com.fyp.crowdlink.domain.model

data class PairingRequest(
    val senderDeviceId: String,
    val senderDisplayName: String,
    val timestamp: Long = System.currentTimeMillis()
)