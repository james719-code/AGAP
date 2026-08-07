package com.travelingsalesman.agap.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.repository.AgencyRepository
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.travelingsalesman.agap.data.local.ServerConfigManager

import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.EmergencyPreferences
import com.travelingsalesman.agap.data.local.EmergencyPreferencesManager
import com.travelingsalesman.agap.data.local.entity.EmergencyContactEntity
import com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity
import com.travelingsalesman.agap.data.local.entity.UserProfileEntity
import com.travelingsalesman.agap.data.repository.UserProfileRepository

data class ProfileUiState(
    val username: String? = null,
    val role: String? = null,
    val userId: Long? = null,
    val agencyId: Long? = null,
    val agencyInfo: AgencyDto? = null,
    val serverUrl: String = "",
    val pendingPacketCount: Int = 0,
    val isLoggedOut: Boolean = false,
    
    // Detailed Profile Data (Functional)
    val userProfile: UserProfileEntity? = null,
    val medicalInfo: MedicalInfoEntity? = null,
    val emergencyContacts: List<EmergencyContactEntity> = emptyList(),
    val preferences: EmergencyPreferences = EmergencyPreferences(),
    val isDarkMode: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val telemetryRepository: TelemetryRepository,
    private val serverConfigManager: ServerConfigManager,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesManager: EmergencyPreferencesManager,
    private val authTokenManager: AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeBaseData()
        observeDetailedProfile()
    }

    private fun observeBaseData() {
        viewModelScope.launch {
            serverConfigManager.serverUrl.collect { url ->
                _uiState.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            authRepository.username.collect { name ->
                _uiState.update { it.copy(username = name) }
            }
        }
        viewModelScope.launch {
            authRepository.userRole.collect { r ->
                _uiState.update { it.copy(role = r) }
            }
        }
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                _uiState.update { it.copy(userId = uid) }
            }
        }
        viewModelScope.launch {
            authRepository.agencyId.collect { aid ->
                _uiState.update { it.copy(agencyId = aid) }
                if (aid != null) {
                    fetchAgency(aid)
                }
            }
        }
        viewModelScope.launch {
            telemetryRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }
    }

    private fun observeDetailedProfile() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                val userId = uid ?: 2L // Default to bypass citizen ID
                
                // Collect User Profile
                launch {
                    userProfileRepository.getUserProfile(userId).collect { profile ->
                        if (profile == null) seedMockData(userId)
                        _uiState.update { it.copy(userProfile = profile) }
                    }
                }
                
                // Collect Medical Info
                launch {
                    userProfileRepository.getMedicalInfo(userId).collect { info ->
                        _uiState.update { it.copy(medicalInfo = info) }
                    }
                }
                
                // Collect Contacts
                launch {
                    userProfileRepository.getEmergencyContacts(userId).collect { contacts ->
                        _uiState.update { it.copy(emergencyContacts = contacts) }
                    }
                }
            }
        }

        // Collect Preferences
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }

        // Collect Dark Mode
        viewModelScope.launch {
            authTokenManager.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
    }

    private suspend fun seedMockData(userId: Long) {
        userProfileRepository.saveUserProfile(
            UserProfileEntity(
                userId = userId,
                fullName = "Juan Dela Cruz",
                dob = "January 1, 1998",
                gender = "Male",
                phone = "0917 123 4567",
                email = "juan.delacruz@email.com",
                address = "Rawis, Legazpi City, Albay",
                citizenId = "AGAP-10284",
                memberSince = "May 17, 2024"
            )
        )
        userProfileRepository.saveMedicalInfo(
            MedicalInfoEntity(
                userId = userId,
                bloodType = "O+",
                allergies = "No known allergies",
                conditions = "None",
                medications = "None",
                emergencyNotes = "N/A"
            )
        )
        userProfileRepository.saveEmergencyContact(
            EmergencyContactEntity(
                userId = userId,
                name = "Maria Cruz (Mother)",
                phone = "0917 123 4567",
                isPrimary = true
            )
        )
    }

    fun updateAutoSendGps(enabled: Boolean) = viewModelScope.launch { preferencesManager.updateAutoSendGps(enabled) }
    fun updateBleFallback(enabled: Boolean) = viewModelScope.launch { preferencesManager.updateBleFallback(enabled) }
    fun updateVibrationAlert(enabled: Boolean) = viewModelScope.launch { preferencesManager.updateVibrationAlert(enabled) }
    fun updateSilentMode(enabled: Boolean) = viewModelScope.launch { preferencesManager.updateSilentMode(enabled) }
    fun updateOfflineSharing(enabled: Boolean) = viewModelScope.launch { preferencesManager.updateOfflineSharing(enabled) }
    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch { authTokenManager.setDarkMode(enabled) }

    private fun fetchAgency(agencyId: Long) {
        viewModelScope.launch {
            val result = agencyRepository.getAgency(agencyId)
            if (result is Result.Success) {
                _uiState.update { it.copy(agencyInfo = result.data) }
            }
        }
    }

    fun updateServerUrl(newUrl: String) {
        viewModelScope.launch {
            serverConfigManager.setServerUrl(newUrl)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }
}
