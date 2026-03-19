package com.fyp.crowdlink.domain.model

data class SosAlertData(
    val friendId: String,
    val senderName: String,
    val latitude: Double?,
    val longitude: Double?,
    val receivedAt: Long
)
