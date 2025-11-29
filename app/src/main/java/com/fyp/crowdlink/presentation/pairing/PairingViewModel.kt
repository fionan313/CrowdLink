package com.fyp.crowdlink.presentation.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.usecase.PairFriendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _myDeviceId = MutableStateFlow(generateDeviceId())
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    fun generateQRCode(): String {
        // Returns device ID to encode in QR
        return myDeviceId.value
    }
    
    fun onQRScanned(scannedDeviceId: String, friendName: String) {
        viewModelScope.launch {
            _pairingState.value = PairingState.Pairing
            try {
                pairFriendUseCase(scannedDeviceId, friendName)
                _pairingState.value = PairingState.Success
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun generateDeviceId(): String {
        // Generate unique ID for this device
        return UUID.randomUUID().toString()
    }
}

sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}