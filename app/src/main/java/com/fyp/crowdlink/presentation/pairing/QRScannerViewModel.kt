package com.fyp.crowdlink.presentation.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _pairingStatus = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()
    
    fun onQRCodeScanned(qrCode: String) {
        viewModelScope.launch {
            try {
                _pairingStatus.value = PairingStatus.Processing
                
                // Parse JSON from QR code
                val json = JSONObject(qrCode)
                val deviceId = json.getString("deviceId")
                val displayName = json.getString("displayName")
                
                // Check if already paired
                val existingFriend = friendRepository.getFriendById(deviceId)
                if (existingFriend != null) {
                    _pairingStatus.value = PairingStatus.AlreadyPaired(displayName)
                    return@launch
                }
                
                // Save friend with their display name
                val friend = Friend(
                    deviceId = deviceId,
                    displayName = displayName,  // ← Use their name from QR!
                    pairedAt = System.currentTimeMillis()
                )
                
                friendRepository.addFriend(friend)
                _pairingStatus.value = PairingStatus.Success(displayName)
                
            } catch (e: Exception) {
                _pairingStatus.value = PairingStatus.Error(e.message ?: "Invalid QR code")
            }
        }
    }
}

sealed class PairingStatus {
    object Idle : PairingStatus()
    object Processing : PairingStatus()
    data class Success(val friendName: String) : PairingStatus()
    data class AlreadyPaired(val friendName: String) : PairingStatus()
    data class Error(val message: String) : PairingStatus()
}