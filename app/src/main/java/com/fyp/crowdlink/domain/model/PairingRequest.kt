package com.fyp.crowdlink.domain.model

data class PairingRequest(
    val senderDeviceId: String,
    val senderDisplayName: String,
    val sharedKey: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)