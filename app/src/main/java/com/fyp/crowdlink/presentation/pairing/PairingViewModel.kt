package com.fyp.crowdlink.presentation.pairing

import android.graphics.Bitmap
import android.os.Build
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
import javax.inject.Inject

/**
 * PairingViewModel
 *
 * This ViewModel manages the logic for the device pairing process.
 * It handles:
 * 1. Generating a QR code containing this device's ID and user profile.
 * 2. Processing scanned QR code data from other devices.
 * 3. Managing the state of the pairing process (Idle, Pairing, Success, Error).
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository
) : ViewModel() {
    
    // StateFlow for the generated QR code image
    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    // StateFlow for the current device's ID
    private val _myDeviceId = MutableStateFlow<String>("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    // StateFlow for tracking the pairing status
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    init {
        loadDeviceId()
    }
    
    /**
     * Loads the unique device ID from persistent storage.
     */
    private fun loadDeviceId() {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
    }
    
    /**
     * Generates a QR code representing the user's identity.
     * The QR code payload contains a JSON string with the device ID and display name.
     */
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                // Get user profile for display name
                val userProfile = userProfileRepository.getUserProfile().first()
                
                // Use the device model name if no display name is set
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val displayName = userProfile?.displayName?.ifBlank { deviceModel } ?: deviceModel
                
                // Create JSON payload
                val qrData = JSONObject().apply {
                    put("deviceId", _myDeviceId.value)
                    put("displayName", displayName)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                // Encode the JSON string into a QR code using ZXing
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                
                // Convert the BitMatrix to a Bitmap
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

    /**
     * Processes the scanned data from a QR code.
     * Parses the JSON to extract the friend's device ID and name, then adds them as a friend.
     *
     * @param scannedData The raw string data scanned from the QR code.
     * @param defaultName A fallback name to use if parsing fails (optional usage).
     */
    fun onQRScanned(scannedData: String, defaultName: String) {
        viewModelScope.launch {
            _pairingState.value = PairingState.Pairing
            try {
                var friendDeviceId = scannedData
                var friendName = defaultName

                // Attempt to parse the scanned data as JSON
                try {
                    val json = JSONObject(scannedData)
                    if (json.has("deviceId")) {
                        friendDeviceId = json.getString("deviceId")
                    }
                    if (json.has("displayName")) {
                        friendName = json.getString("displayName")
                    }
                } catch (e: Exception) {
                    // Parsing failed, assume the data is just the device ID directly
                    // Use default name provided
                }

                if (friendDeviceId.isNotBlank()) {
                    // Create and save the new Friend entity
                    val friend = Friend(
                        deviceId = friendDeviceId,
                        displayName = friendName,
                        pairedAt = System.currentTimeMillis(),
                        lastSeen = System.currentTimeMillis()
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

/**
 * Represents the various states of the pairing process.
 */
sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}
