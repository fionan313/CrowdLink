package com.fyp.crowdlink.presentation.pairing

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.PairFriendUseCase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    private val _myDeviceId = MutableStateFlow<String>("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    init {
        loadDeviceId()
    }
    
    private fun loadDeviceId() {
        val deviceId = getPersistentDeviceId()
        _myDeviceId.value = deviceId
    }
    
    private fun getPersistentDeviceId(): String {
        val key = "device_id"
        return sharedPreferences.getString(key, null) ?: run {
            val newId = UUID.randomUUID().toString()
            sharedPreferences.edit { putString(key, newId) }
            newId
        }
    }
    
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                // Get user profile for display name
                val userProfile = userProfileRepository.getUserProfile().first()
                val displayName = userProfile?.displayName ?: "Anonymous"
                
                // Create JSON with device ID and display name
                val qrData = JSONObject().apply {
                    put("deviceId", _myDeviceId.value)
                    put("displayName", displayName)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                // Generate QR code bitmap
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(
                            x, 
                            y, 
                            if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                        )
                    }
                }
                
                _qrCodeBitmap.value = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                _qrCodeBitmap.value = null
            }
        }
    }

    fun onQRScanned(scannedData: String, defaultName: String) {
        viewModelScope.launch {
            _pairingState.value = PairingState.Pairing
            try {
                var friendDeviceId = scannedData
                var friendName = defaultName

                // Try to parse as JSON
                try {
                    val json = JSONObject(scannedData)
                    if (json.has("deviceId")) {
                        friendDeviceId = json.getString("deviceId")
                    }
                    if (json.has("displayName")) {
                        friendName = json.getString("displayName")
                    }
                } catch (e: Exception) {
                    // Not JSON or invalid format, assume raw ID if not empty
                    // Keep default values
                }

                if (friendDeviceId.isNotBlank()) {
                    val friend = Friend(
                        deviceId = friendDeviceId,
                        shortId = friendDeviceId.take(16),
                        displayName = friendName
                    )
                    friendRepository.addFriend(friend)
                    _pairingState.value = PairingState.Success
                } else {
                     _pairingState.value = PairingState.Error("Invalid QR Code")
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}