package com.fyp.crowdlink.presentation.pairing

sealed class PairingStatus {
    object Idle : PairingStatus()
    data class Success(val friendName: String) : PairingStatus()
    data class AlreadyPaired(val friendName: String) : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}