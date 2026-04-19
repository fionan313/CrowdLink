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
 * Orchestrates the two-phase QR pairing handshake. Device A generates a QR code
 * containing its device ID, display name and a fresh AES-256-GCM shared key.
 * Device B scans it, sends a BLE GATT confirmation back, and both devices persist
 * the resulting [Friend] record to Room. Incoming requests from the other direction
 * are also handled here via [incomingPairingRequest].
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

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // polled every 2 seconds to keep the UI aware of radio state
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

    private val _showDebugInfo = MutableStateFlow(
        sharedPreferences.getBoolean("show_pairing_debug", false)
    )
    val showDebugInfo: StateFlow<Boolean> = _showDebugInfo.asStateFlow()

    // keep debug panel visibility in sync with the settings preference
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "show_pairing_debug") {
            _showDebugInfo.value = prefs.getBoolean(key, false)
        }
    }

    val incomingPairingRequest: StateFlow<PairingRequest?> = deviceRepository.incomingPairingRequest
    val isGattServerReady: StateFlow<Boolean> = deviceRepository.isGattServerReady
    val lastGattError: StateFlow<Pair<Int, Long>?> = deviceRepository.lastGattError

    // aggregates GATT state, pairing state and key presence into a single debug string
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

    // session state for the in-progress pairing attempt
    private var pendingFriendDeviceId: String? = null
    private var pendingFriendName: String? = null
    private var pendingSharedKey: String? = null // key generated with this device's QR
    private var scannedSharedKey: String? = null // key extracted from the scanned QR

    init {
        loadDeviceId()
        observePairingAccepted()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun loadDeviceId() {
        _myDeviceId.value = userProfileRepository.getPersistentDeviceId()
    }

    /**
     * Observes the [pairingAccepted] event from [DeviceRepository]. When the remote device
     * confirms the pairing, the friend record is persisted to Room and the state transitions
     * to [PairingState.Success]. Duplicate entries are guarded against by checking Room first.
     */
    private fun observePairingAccepted() {
        deviceRepository.pairingAccepted
            .onEach { acceptedDeviceId ->
                Timber.tag("PairingViewModel").d("Pairing accepted by $acceptedDeviceId")
                if (acceptedDeviceId == pendingFriendDeviceId) {
                    viewModelScope.launch {
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
     * Generates a QR code bitmap containing this device's ID, display name and a fresh
     * AES-256-GCM shared key. The key is stored in [pendingSharedKey] so it can be
     * confirmed when Device B sends it back in the GATT pairing request.
     */
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                val userProfile = userProfileRepository.getUserProfile().first()
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val displayName = userProfile?.displayName?.ifBlank { deviceModel } ?: deviceModel

                val sharedKey = encryptionManager.generateSharedKey()
                pendingSharedKey = sharedKey
                // clear stale session state from any previous pairing attempt
                pendingFriendDeviceId = null
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

    /**
     * Called after Device B scans Device A's QR code. Extracts the friend's identity
     * and shared key from the payload, then polls BLE discovery for up to 15 seconds
     * waiting for the target device to become visible before sending the pairing request.
     * Transitions to [PairingState.AwaitingConfirmation] once sent, with a 20-second
     * timeout before emitting an error if no acceptance arrives.
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
                    if (json.has("deviceId")) friendDeviceId = json.getString("deviceId")
                    if (json.has("displayName")) friendName = json.getString("displayName")
                    if (json.has("sharedKey")) scannedSharedKey = json.getString("sharedKey")
                } catch (_: Exception) {
                    // fallback - treat the raw string as a device ID for legacy compatibility
                    friendDeviceId = scannedData
                }

                if (friendDeviceId.isNotBlank()) {
                    pendingFriendDeviceId = friendDeviceId
                    pendingFriendName = friendName

                    val userProfile = userProfileRepository.getUserProfile().first()
                    val myDisplayName = userProfile?.displayName ?: "${Build.MANUFACTURER} ${Build.MODEL}"

                    bleScanner.startDiscovery()

                    // poll for the target device for up to 15 seconds before giving up
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
                        // 20-second window for the peer to accept before showing an error
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
     * Accepts an incoming pairing request. Saves the requester as a friend with the
     * shared key from the request (or falls back to the scanned/pending key), sends
     * a GATT acceptance back to the requester, and transitions to [PairingState.Success].
     */
    fun acceptPairingRequest(request: PairingRequest) {
        viewModelScope.launch {
            friendRepository.addFriend(Friend(
                deviceId = request.senderDeviceId,
                displayName = request.senderDisplayName,
                sharedKey = request.sharedKey ?: scannedSharedKey ?: pendingSharedKey,
                pairedAt = System.currentTimeMillis()
            ))
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
 * Represents the lifecycle of a single pairing attempt from idle through to success or failure.
 */
sealed class PairingState {
    object Idle : PairingState()                        // no pairing in progress
    object Pairing : PairingState()                     // scanning for the target device
    object AwaitingConfirmation : PairingState()        // request sent, waiting for acceptance
    object Success : PairingState()                     // both sides have persisted the friend record
    data class Error(val message: String) : PairingState() // something went wrong, message shown inline
}