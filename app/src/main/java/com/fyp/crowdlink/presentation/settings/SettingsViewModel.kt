package com.fyp.crowdlink.presentation.settings

import android.content.SharedPreferences
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

sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
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
                
                // Reset after 2 seconds
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

    fun setLocationSharing(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("location_sharing", enabled) }
        _locationSharing.value = enabled
        if (!enabled) {
            viewModelScope.launch {
                locationRepository.clearAllFriendLocations()
            }
        }
    }

    fun setForceShowRelays(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("force_show_relays", enabled) }
        _forceShowRelays.value = enabled
    }

    fun clearMessageHistory() {
        viewModelScope.launch {
            messageRepository.clearAllMessages()
        }
    }

    fun clearMapCache() {
        viewModelScope.launch {
            locationRepository.clearMapCache()
        }
    }

    fun resetOnboarding() {
        sharedPreferences.edit { putBoolean("onboarding_complete", false) }
    }

    fun unpairAllFriends() {
        viewModelScope.launch {
            friendRepository.unpairAllFriends()
        }
    }

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
