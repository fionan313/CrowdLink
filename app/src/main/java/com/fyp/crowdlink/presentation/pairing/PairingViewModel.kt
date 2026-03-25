package com.fyp.crowdlink.presentation.pairing

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.domain.model.Friend
import com.fyp.crowdlink.domain.model.PairingRequest
import com.fyp.crowdlink.domain.repository.DeviceRepository
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import com.fyp.crowdlink.domain.usecase.PairFriendUseCase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * PairingViewModel
 *
 * This ViewModel manages the logic for the device pairing process.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userProfileRepository: UserProfileRepository,
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository,
    private val deviceRepository: DeviceRepository,
    private val bleScanner: BleScanner
) : ViewModel() {
    
    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    val isBluetoothEnabled: StateFlow<Boolean> = flow {
        while (true) {
            emit(bluetoothAdapter?.isEnabled == true)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // StateFlow for the generated QR code image
    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    // StateFlow for the current device's ID
    private val _myDeviceId = MutableStateFlow("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    // StateFlow for tracking the pairing status
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    val incomingPairingRequest: StateFlow<PairingRequest?> = deviceRepository.incomingPairingRequest
    
    private var pendingFriendDeviceId: String? = null
    private var pendingFriendName: String? = null

    init {
        loadDeviceId()
        observePairingAccepted()
    }
    
    private fun loadDeviceId() {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
    }

    private fun observePairingAccepted() {
        deviceRepository.pairingAccepted
            .onEach { acceptedDeviceId ->
                Timber.tag("PairingViewModel").d("Pairing accepted by $acceptedDeviceId")
                if (acceptedDeviceId == pendingFriendDeviceId) {
                    viewModelScope.launch {
                        friendRepository.addFriend(Friend(
                            deviceId = acceptedDeviceId,
                            displayName = pendingFriendName ?: "Unknown Friend",
                            pairedAt = System.currentTimeMillis()
                        ))
                        _pairingState.value = PairingState.Success
                        Timber.tag("PairingViewModel").d("Pairing SUCCESS for $acceptedDeviceId")
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                val userProfile = userProfileRepository.getUserProfile().first()
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val displayName = userProfile?.displayName?.ifBlank { deviceModel } ?: deviceModel
                
                val qrData = JSONObject().apply {
                    put("deviceId", _myDeviceId.value)
                    put("displayName", displayName)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
                
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap[x, y] =
                            if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
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
                var friendDeviceId = ""
                var friendName = defaultName

                try {
                    val json = JSONObject(scannedData)
                    if (json.has("deviceId")) {
                        friendDeviceId = json.getString("deviceId")
                    }
                    if (json.has("displayName")) {
                        friendName = json.getString("displayName")
                    }
                } catch (_: Exception) {
                    friendDeviceId = scannedData
                }

                if (friendDeviceId.isNotBlank()) {
                    pendingFriendDeviceId = friendDeviceId
                    pendingFriendName = friendName

                    val userProfile = userProfileRepository.getUserProfile().first()
                    val myDisplayName = userProfile?.displayName ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                    
                    // Retry sending pairing request until device is found or timeout
                    val timeoutMs = 15_000L
                    val startTime = System.currentTimeMillis()
                    var sent = false

                    while (!sent && System.currentTimeMillis() - startTime < timeoutMs) {
                        val device = bleScanner.getDeviceById(friendDeviceId)
                        if (device != null) {
                            deviceRepository.sendPairingRequest(
                                targetDeviceId = friendDeviceId,
                                senderDisplayName = myDisplayName
                            )
                            sent = true
                        } else {
                            delay(1000)
                        }
                    }

                    if (sent) {
                        _pairingState.value = PairingState.AwaitingConfirmation
                    } else {
                        _pairingState.value = PairingState.Error(
                            "Could not find friend nearby. Make sure both devices have CrowdLink open."
                        )
                    }
                } else {
                     _pairingState.value = PairingState.Error("Invalid QR Code")
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun acceptPairingRequest(request: PairingRequest) {
        viewModelScope.launch {
            // Save requester as friend on Device B
            friendRepository.addFriend(Friend(
                deviceId = request.senderDeviceId,
                displayName = request.senderDisplayName,
                pairedAt = System.currentTimeMillis()
            ))
            // Send acceptance back so Device A saves Device B
            deviceRepository.sendPairingAccepted(targetDeviceId = request.senderDeviceId)
            deviceRepository.clearIncomingPairingRequest()
            _pairingState.value = PairingState.Success
        }
    }

    fun declinePairingRequest() {
        deviceRepository.clearIncomingPairingRequest()
    }
}

sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object AwaitingConfirmation : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}
