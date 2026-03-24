package com.fyp.crowdlink.presentation.pairing

import androidx.lifecycle.ViewModel
import com.fyp.crowdlink.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _pairingStatus = MutableStateFlow<PairingStatus>(PairingStatus.Idle)

}

sealed class PairingStatus {
    object Idle : PairingStatus()
    object Processing : PairingStatus()
    data class Success(val friendName: String) : PairingStatus()
    data class AlreadyPaired(val friendName: String) : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}