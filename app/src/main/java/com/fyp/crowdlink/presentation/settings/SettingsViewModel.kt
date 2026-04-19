package com.fyp.crowdlink.presentation.settings

import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import com.fyp.crowdlink.data.service.MeshService
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.UserProfile
import com.fyp.crowdlink.domain.repository.FriendRepository
import com.fyp.crowdlink.domain.repository.LocationRepository
import com.fyp.crowdlink.domain.repository.MessageRepository
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.edit

/**
 * Represents the lifecycle of a profile save operation.
 */
sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

/**
 * SettingsViewModel
 *
 * Manages all settings state. Each toggle is backed by a [MutableStateFlow] initialised
 * from SharedPreferences and written back on change. Background mesh state also starts
 * or stops [MeshService] directly. [resetAppData] wipes all local Room tables and
 * SharedPreferences in a single coroutine.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userProfileRepository: UserProfileRepository,
    private val friendRepository: FriendRepository,
    private val messageRepository: MessageRepository,
    private val sharedPreferences: SharedPreferences,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    // each preference is mirrored into a StateFlow so the UI reacts immediately on toggle
    private val _autoStart = MutableStateFlow(sharedPreferences.getBoolean("auto_start", true))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _meshRelay = MutableStateFlow(sharedPreferences.getBoolean("mesh_relay", true))
    val meshRelay: StateFlow<Boolean> = _meshRelay.asStateFlow()

    private val _esp32Scanning = MutableStateFlow(sharedPreferences.getBoolean("esp32_scanning", false))
    val esp32Scanning: StateFlow<Boolean> = _esp32Scanning.asStateFlow()

    private val _ghostMode = MutableStateFlow(sharedPreferences.getBoolean("ghost_mode", false))
    val ghostMode: StateFlow<Boolean> = _ghostMode.asStateFlow()

    private val _locationSharing = MutableStateFlow(sharedPreferences.getBoolean("location_sharing", true))
    val locationSharing: StateFlow<Boolean> = _locationSharing.asStateFlow()

    private val _forceShowRelays = MutableStateFlow(sharedPreferences.getBoolean("force_show_relays", false))
    val forceShowRelays: StateFlow<Boolean> = _forceShowRelays.asStateFlow()

    private val _indoorOverride = MutableStateFlow(sharedPreferences.getBoolean("indoor_override", false))
    val indoorOverride: StateFlow<Boolean> = _indoorOverride.asStateFlow()

    private val _wifiDirectMode = MutableStateFlow(sharedPreferences.getBoolean("wifi_direct_mode", false))
    val wifiDirectMode: StateFlow<Boolean> = _wifiDirectMode.asStateFlow()

    private val _showPairingDebug = MutableStateFlow(sharedPreferences.getBoolean("show_pairing_debug", false))
    val showPairingDebug: StateFlow<Boolean> = _showPairingDebug.asStateFlow()

    private val _backgroundMesh = MutableStateFlow(sharedPreferences.getBoolean("background_mesh", false))
    val backgroundMesh: StateFlow<Boolean> = _backgroundMesh.asStateFlow()

    // live count of paired friends shown in the About section
    val pairedFriendsCount: StateFlow<Int> = friendRepository.getAllFriends()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deviceId: String = userProfileRepository.getPersistentDeviceId()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile ?: UserProfile(
                    displayName = "Anonymous",
                    phoneNumber = null,
                    statusMessage = null
                )
            }
        }
    }

    /**
     * Persists updated profile fields to Room. Resets [saveStatus] to Idle after
     * 2 seconds so the success card in the UI dismisses automatically.
     */
    fun saveUserProfile(
        displayName: String,
        phoneNumber: String?,
        statusMessage: String?
    ) {
        viewModelScope.launch {
            try {
                _saveStatus.value = SaveStatus.Saving
                val profile = UserProfile(
                    displayName = displayName.trim(),
                    phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                    statusMessage = statusMessage?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAt = System.currentTimeMillis()
                )
                userProfileRepository.saveUserProfile(profile)
                _saveStatus.value = SaveStatus.Success
                kotlinx.coroutines.delay(2000)
                _saveStatus.value = SaveStatus.Idle
            } catch (e: Exception) {
                _saveStatus.value = SaveStatus.Error(e.message ?: "Failed to save")
            }
        }
    }

    fun setAutoStart(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("auto_start", enabled) }
        _autoStart.value = enabled
    }

    fun setMeshRelay(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("mesh_relay", enabled) }
        _meshRelay.value = enabled
    }

    fun setEsp32Scanning(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("esp32_scanning", enabled) }
        _esp32Scanning.value = enabled
    }

    fun setGhostMode(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("ghost_mode", enabled) }
        _ghostMode.value = enabled
    }

    /**
     * Toggles location sharing. When disabled, cached friend locations are cleared
     * from Room immediately so stale pins no longer appear on the map.
     */
    fun setLocationSharing(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("location_sharing", enabled) }
        _locationSharing.value = enabled
        if (!enabled) {
            viewModelScope.launch { locationRepository.clearAllFriendLocations() }
        }
    }

    fun setForceShowRelays(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("force_show_relays", enabled) }
        _forceShowRelays.value = enabled
    }

    fun setIndoorOverride(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("indoor_override", enabled) }
        _indoorOverride.value = enabled
    }

    fun setWifiDirectMode(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("wifi_direct_mode", enabled) }
        _wifiDirectMode.value = enabled
    }

    fun setShowPairingDebug(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("show_pairing_debug", enabled) }
        _showPairingDebug.value = enabled
    }

    /**
     * Toggles the background mesh foreground service. Starting it allows BLE and
     * location broadcasting to continue after the app leaves the foreground.
     */
    fun setBackgroundMesh(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("background_mesh", enabled) }
        _backgroundMesh.value = enabled
        val intent = Intent(context, MeshService::class.java)
        if (enabled) context.startForegroundService(intent) else context.stopService(intent)
    }

    fun clearMessageHistory() {
        viewModelScope.launch { messageRepository.clearAllMessages() }
    }

    fun clearMapCache() {
        viewModelScope.launch { locationRepository.clearMapCache() }
    }

    fun resetOnboarding() {
        sharedPreferences.edit { putBoolean("onboarding_complete", false) }
    }

    fun unpairAllFriends() {
        viewModelScope.launch { friendRepository.unpairAllFriends() }
    }

    /**
     * Full app reset - clears all Room tables, cached locations, map tiles and
     * SharedPreferences in a single coroutine. The app will behave as if freshly installed.
     */
    fun resetAppData() {
        viewModelScope.launch {
            messageRepository.clearAllMessages()
            locationRepository.clearAllFriendLocations()
            locationRepository.clearMapCache()
            friendRepository.unpairAllFriends()
            userProfileRepository.clearUserProfile()
            sharedPreferences.edit { clear() }
        }
    }
}