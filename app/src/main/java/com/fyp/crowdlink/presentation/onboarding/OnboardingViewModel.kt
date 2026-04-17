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
 * handles transient state for user registration and persists the local mesh identity.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _isNameValid = MutableStateFlow(false)
    val isNameValid: StateFlow<Boolean> = _isNameValid.asStateFlow()

    fun onNameChanged(name: String) {
        _displayName.value = name
        // enforce basic length constraints for mesh discoverability
        _isNameValid.value = name.trim().length >= 2
    }

    /**
     * saveProfile
     *
     * commits the chosen pseudonym to the local repository to initialise the mesh node.
     */
    fun saveProfile(onComplete: () -> Unit) {
        viewModelScope.launch {
            val name = _displayName.value.trim()
                .ifBlank { "Festival Goer" }  // fallback pseudonym if validation is bypassed
            
            val profile = UserProfile(
                displayName = name,
                updatedAt = System.currentTimeMillis()
            )
            userProfileRepository.saveUserProfile(profile)
            onComplete()
        }
    }
}
