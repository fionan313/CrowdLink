package com.fyp.crowdlink.presentation.pairing

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.data.ble.BleScanner
import com.fyp.crowdlink.data.crypto.EncryptionManager
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
 * orchestrates the secure cryptographic handshake between peers.
 * manages QR generation, GATT-based pairing requests, and bond persistence.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userProfileRepository: UserProfileRepository,
    private val pairFriendUseCase: PairFriendUseCase,
    private val friendRepository: FriendRepository,
    private val deviceRepository: DeviceRepository,
    private val bleScanner: BleScanner,
    private val encryptionManager: EncryptionManager,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    
    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // real-time radio status for UI feedback
    val isBluetoothEnabled: StateFlow<Boolean> = flow {
        while (true) {
            emit(bluetoothAdapter?.isEnabled == true)
            delay(2000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()
    
    private val _myDeviceId = MutableStateFlow("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val _showDebugInfo = MutableStateFlow(sharedPreferences.getBoolean("show_pairing_debug", false))
    val showDebugInfo: StateFlow<Boolean> = _showDebugInfo.asStateFlow()

    // reactively update debug visibility when settings change
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "show_pairing_debug") {
            _showDebugInfo.value = prefs.getBoolean(key, false)
        }
    }

    val incomingPairingRequest: StateFlow<PairingRequest?> = deviceRepository.incomingPairingRequest
    
    val isGattServerReady: StateFlow<Boolean> = deviceRepository.isGattServerReady
    
    val lastGattError: StateFlow<Pair<Int, Long>?> = deviceRepository.lastGattError
    
    // aggregate telemetry for mesh diagnostics
    val debugInfo: StateFlow<String> = combine(
        isGattServerReady,
        _pairingState,
        incomingPairingRequest,
        lastGattError
    ) { gattReady, state, request, gattError ->
        val sb = StringBuilder()
        sb.append("GATT Server: ${if (gattReady) "READY" else "NOT READY"}\n")
        sb.append("Local SharedKey: ${if (pendingSharedKey != null) "SET" else "NULL"}\n")
        sb.append("Scanned SharedKey: ${if (scannedSharedKey != null) "SET" else "NULL"}\n")
        if (request != null) {
            sb.append("Incoming SharedKey: ${if (request.sharedKey != null) "SET" else "NULL"}\n")
        }
        if (gattError != null) {
            val (code, time) = gattError
            val secondsAgo = (System.currentTimeMillis() - time) / 1000
            sb.append("Last GATT Error: $code (${secondsAgo}s ago)")
            if (code == 133) sb.append(" (GATT_ERROR - try toggling BT)")
        }
        sb.toString()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private var pendingFriendDeviceId: String? = null
    private var pendingFriendName: String? = null
    private var pendingSharedKey: String? = null

    init {
        loadDeviceId()
        observePairingAccepted()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }
    
    override fun onCleared() {
        super.onCleared()
        // prevent listener leaks
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
    
    private fun loadDeviceId() {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
    }

    /**
     * observePairingAccepted
     *
     * monitors GATT events to finalise bond persistence once a peer confirms.
     */
    private fun observePairingAccepted() {
        deviceRepository.pairingAccepted
            .onEach { acceptedDeviceId ->
                Timber.tag("PairingViewModel").d("Pairing accepted by $acceptedDeviceId")
                if (acceptedDeviceId == pendingFriendDeviceId) {
                    viewModelScope.launch {
                        // avoid duplicate entries if both sides initiate
                        val existing = friendRepository.getFriendById(acceptedDeviceId)
                        if (existing == null) {
                            friendRepository.addFriend(Friend(
                                deviceId = acceptedDeviceId,
                                displayName = pendingFriendName ?: "Unknown Friend",
                                sharedKey = scannedSharedKey,
                                pairedAt = System.currentTimeMillis()
                            ))
                        }
                        _pairingState.value = PairingState.Success
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * generateQRCode
     *
     * serialises local identity and a fresh ephemeral shared key into a QR bitmap.
     */
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                val userProfile = userProfileRepository.getUserProfile().first()
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val displayName = userProfile?.displayName?.ifBlank { deviceModel } ?: deviceModel
                
                val sharedKey = encryptionManager.generateSharedKey()
                pendingSharedKey = sharedKey
                pendingFriendDeviceId = null  // invalidate stale session data
                pendingFriendName = null

                val qrData = JSONObject().apply {
                    put("deviceId", _myDeviceId.value)
                    put("displayName", displayName)
                    put("sharedKey", sharedKey)
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
                Timber.e(e, "QR generation failed")
                _qrCodeBitmap.value = null
            }
        }
    }

    private var scannedSharedKey: String? = null

    /**
     * onQRScanned
     *
     * parses peer identity from QR and initiates the GATT-based pairing request.
     */
    fun onQRScanned(scannedData: String, defaultName: String) {
        viewModelScope.launch {
            _pairingState.value = PairingState.Pairing
            try {
                var friendDeviceId = ""
                var friendName = defaultName
                scannedSharedKey = null

                try {
                    val json = JSONObject(scannedData)
                    if (json.has("deviceId")) {
                        friendDeviceId = json.getString("deviceId")
                    }
                    if (json.has("displayName")) {
                        friendName = json.getString("displayName")
                    }
                    if (json.has("sharedKey")) {
                        scannedSharedKey = json.getString("sharedKey")
                    }
                } catch (_: Exception) {
                    friendDeviceId = scannedData
                }

                if (friendDeviceId.isNotBlank()) {
                    pendingFriendDeviceId = friendDeviceId
                    pendingFriendName = friendName

                    val userProfile = userProfileRepository.getUserProfile().first()
                    val myDisplayName = userProfile?.displayName ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                    
                    // ensure discovery is active for target node resolution
                    bleScanner.startDiscovery()
                    
                    // poll for target node availability before dispatching pairing request
                    val timeoutMs = 15_000L
                    val startTime = System.currentTimeMillis()
                    var sent = false

                    while (!sent && System.currentTimeMillis() - startTime < timeoutMs) {
                        val device = bleScanner.getDeviceById(friendDeviceId)
                        if (device != null) {
                            deviceRepository.sendPairingRequest(
                                targetDeviceId = friendDeviceId,
                                senderDisplayName = myDisplayName,
                                sharedKey = scannedSharedKey
                            )
                            sent = true
                        } else {
                            delay(1000)
                        }
                    }

                    if (sent) {
                        _pairingState.value = PairingState.AwaitingConfirmation
                        // timeout if the peer fails to confirm within the window
                        viewModelScope.launch {
                            delay(20_000)
                            if (_pairingState.value is PairingState.AwaitingConfirmation) {
                                _pairingState.value = PairingState.Error(
                                    "Friend did not respond. Try again with both devices nearby."
                                )
                            }
                        }
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

    /**
     * acceptPairingRequest
     *
     * confirms a bond request from a peer and saves their credentials for mesh comms.
     */
    fun acceptPairingRequest(request: PairingRequest) {
        viewModelScope.launch {
            friendRepository.addFriend(Friend(
                deviceId = request.senderDeviceId,
                displayName = request.senderDisplayName,
                sharedKey = request.sharedKey ?: scannedSharedKey ?: pendingSharedKey,
                pairedAt = System.currentTimeMillis()
            ))
            // inform requester to finalise the bidirectional bond
            deviceRepository.sendPairingAccepted(targetDeviceId = request.senderDeviceId)
            deviceRepository.clearIncomingPairingRequest()
            _pairingState.value = PairingState.Success
        }
    }

    fun declinePairingRequest() {
        deviceRepository.clearIncomingPairingRequest()
    }
}

/**
 * PairingState
 *
 * finite state representation of the secure handshake lifecycle.
 */
sealed class PairingState {
    object Idle : PairingState()
    object Pairing : PairingState()
    object AwaitingConfirmation : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}
