package com.fyp.crowdlink.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fyp.crowdlink.domain.model.UserProfile
import com.fyp.crowdlink.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OnboardingViewModel
 *
 * Manages transient state for the onboarding flow and persists the user's display name
 * to Room on completion. The saved name becomes the device's identity on the BLE mesh
 * and is included in pairing requests and discovery advertisements.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _isNameValid = MutableStateFlow(false)
    val isNameValid: StateFlow<Boolean> = _isNameValid.asStateFlow()

    /**
     * Called on every keystroke in the name field. Updates the display name and
     * revalidates - the "Get Started" button stays disabled until the name is at
     * least 2 characters after trimming.
     */
    fun onNameChanged(name: String) {
        _displayName.value = name
        _isNameValid.value = name.trim().length >= 2
    }

    /**
     * Persists the display name as a [UserProfile] and invokes [onComplete] to
     * navigate away from onboarding. Falls back to "Festival Goer" if the name
     * is somehow blank when this is called.
     */
    fun saveProfile(onComplete: () -> Unit) {
        viewModelScope.launch {
            val name = _displayName.value.trim().ifBlank { "Festival Goer" }
            userProfileRepository.saveUserProfile(
                UserProfile(
                    displayName = name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onComplete()
        }
    }
}