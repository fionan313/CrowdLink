package com.fyp.crowdlink.presentation.pairing

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.DeviceRepositoryImpl
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.PairFriendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository,
    private val userProfileRepository: UserProfileRepository,
    private val deviceRepository: DeviceRepositoryImpl, // Injecting Implementation to access advertising functions
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    
    private val _myDeviceId = MutableStateFlow(getPersistentDeviceId())
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    private val _qrContent = MutableStateFlow("")
    val qrContent: StateFlow<String> = _qrContent.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    init {
        generateQRContent()
        // Start advertising so the other device can find us via BLE immediately after scanning
        startAdvertising()
    }
    
    private fun generateQRContent() {
        viewModelScope.launch {
            val userProfile = userProfileRepository.getUserProfile().first()
            val displayName = userProfile?.displayName ?: "Unknown User"
            // Format: deviceId|displayName
            _qrContent.value = "${_myDeviceId.value}|$displayName"
        }
    }
    
    private fun startAdvertising() {
        try {
            deviceRepository.startAdvertising(_myDeviceId.value)
        } catch (e: SecurityException) {
            // Handle missing permission
        }
    }

    fun onQRScanned(scannedData: String, defaultName: String) {
        viewModelScope.launch {
            _pairingState.value = PairingState.Pairing
            try {
                // Parse format: deviceId|displayName
                val parts = scannedData.split("|")
                val friendDeviceId = parts[0]
                // Use the name from QR if available, otherwise fallback
                val friendName = if (parts.size > 1) parts[1] else defaultName

                pairFriendUseCase(friendDeviceId, friendName)
                _pairingState.value = PairingState.Success
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun getPersistentDeviceId(): String {
        val key = "device_id"
        var id = sharedPreferences.getString(key, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(key, id).apply()
        }
        return id!!
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            deviceRepository.stopAdvertising()
        } catch (e: SecurityException) {
            // Handle error
        }
    }
}

sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}