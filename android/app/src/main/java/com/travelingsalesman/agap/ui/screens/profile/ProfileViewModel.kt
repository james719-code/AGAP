package com.travelingsalesman.agap.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.UserPreferencesManager
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
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

data class ProfileUiState(
    val username: String? = null,
    val role: String? = null,
    val userId: Long? = null,
    val userDisplayId: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val medicalInfo: String? = null,
    val nationalId: String? = null,
    val isVerified: Boolean = false,
    val agencyId: Long? = null,
    val agencyInfo: AgencyDto? = null,
    val serverUrl: String = "",
    val pendingPacketCount: Int = 0,
    val isLoggedOut: Boolean = false,

    // Emergency Contacts
    val emergencyContacts: List<EmergencyContactDto> = emptyList(),
    val isLoadingContacts: Boolean = false,
    val contactError: String? = null,

    // Preferences (DataStore)
    val autoSendGps: Boolean = true,
    val enableBleFallback: Boolean = true,
    val vibrationAlert: Boolean = true,
    val offlineSharing: Boolean = true,

    // Profile Edit Dialog
    val isEditingProfile: Boolean = false,
    val isSavingProfile: Boolean = false,
    val profileMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val telemetryRepository: TelemetryRepository,
    private val serverConfigManager: ServerConfigManager,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Collect Auth flows
        viewModelScope.launch {
            authRepository.username.collect { name -> _uiState.update { it.copy(username = name) } }
        }
        viewModelScope.launch {
            authRepository.userRole.collect { r -> _uiState.update { it.copy(role = r) } }
        }
        viewModelScope.launch {
            authRepository.userId.collect { uid -> _uiState.update { it.copy(userId = uid) } }
        }
        viewModelScope.launch {
            authRepository.userDisplayId.collect { did -> _uiState.update { it.copy(userDisplayId = did) } }
        }
        viewModelScope.launch {
            authRepository.userFullName.collect { fn -> _uiState.update { it.copy(fullName = fn) } }
        }
        viewModelScope.launch {
            authRepository.userEmail.collect { em -> _uiState.update { it.copy(email = em) } }
        }
        viewModelScope.launch {
            authRepository.userPhone.collect { ph -> _uiState.update { it.copy(phoneNumber = ph) } }
        }
        viewModelScope.launch {
            authRepository.userMedicalInfo.collect { med -> _uiState.update { it.copy(medicalInfo = med) } }
        }
        viewModelScope.launch {
            authRepository.userNationalId.collect { nid -> _uiState.update { it.copy(nationalId = nid) } }
        }
        viewModelScope.launch {
            authRepository.userIsVerified.collect { ver -> _uiState.update { it.copy(isVerified = ver) } }
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
            serverConfigManager.serverUrl.collect { url -> _uiState.update { it.copy(serverUrl = url) } }
        }
        viewModelScope.launch {
            telemetryRepository.getPendingCount().collect { count -> _uiState.update { it.copy(pendingPacketCount = count) } }
        }

        // Collect Preferences flows
        viewModelScope.launch {
            userPreferencesManager.autoSendGps.collect { enabled -> _uiState.update { it.copy(autoSendGps = enabled) } }
        }
        viewModelScope.launch {
            userPreferencesManager.enableBleFallback.collect { enabled -> _uiState.update { it.copy(enableBleFallback = enabled) } }
        }
        viewModelScope.launch {
            userPreferencesManager.vibrationAlert.collect { enabled -> _uiState.update { it.copy(vibrationAlert = enabled) } }
        }
        viewModelScope.launch {
            userPreferencesManager.offlineSharing.collect { enabled -> _uiState.update { it.copy(offlineSharing = enabled) } }
        }

        // Load profile and emergency contacts from API
        fetchProfile()
        fetchEmergencyContacts()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            val result = authRepository.getProfile()
            if (result is Result.Success) {
                val u = result.data
                _uiState.update {
                    it.copy(
                        fullName = u.name,
                        email = u.email,
                        phoneNumber = u.phoneNumber,
                        medicalInfo = u.medicalInfo,
                        nationalId = u.nationalId,
                        isVerified = u.isVerified,
                        emergencyContacts = u.emergencyContacts
                    )
                }
            }
        }
    }

    fun fetchEmergencyContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactError = null) }
            val res = authRepository.listEmergencyContacts()
            if (res is Result.Success) {
                _uiState.update { it.copy(isLoadingContacts = false, emergencyContacts = res.data) }
            } else if (res is Result.Error) {
                _uiState.update { it.copy(isLoadingContacts = false, contactError = res.message) }
            }
        }
    }

    fun addEmergencyContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactError = null) }
            val res = authRepository.addEmergencyContact(name, phone, relation)
            if (res is Result.Success) {
                fetchEmergencyContacts()
            } else if (res is Result.Error) {
                _uiState.update { it.copy(isLoadingContacts = false, contactError = res.message) }
            }
        }
    }

    fun deleteEmergencyContact(contactId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContacts = true, contactError = null) }
            val res = authRepository.deleteEmergencyContact(contactId)
            if (res is Result.Success) {
                fetchEmergencyContacts()
            } else if (res is Result.Error) {
                _uiState.update { it.copy(isLoadingContacts = false, contactError = res.message) }
            }
        }
    }

    fun setAutoSendGps(enabled: Boolean) = viewModelScope.launch { userPreferencesManager.setAutoSendGps(enabled) }
    fun setEnableBleFallback(enabled: Boolean) = viewModelScope.launch { userPreferencesManager.setEnableBleFallback(enabled) }
    fun setVibrationAlert(enabled: Boolean) = viewModelScope.launch { userPreferencesManager.setVibrationAlert(enabled) }
    fun setOfflineSharing(enabled: Boolean) = viewModelScope.launch { userPreferencesManager.setOfflineSharing(enabled) }

    fun openEditProfileDialog() {
        _uiState.update { it.copy(isEditingProfile = true, profileMessage = null) }
    }

    fun closeEditProfileDialog() {
        _uiState.update { it.copy(isEditingProfile = false) }
    }

    fun updateProfile(name: String, email: String, phone: String, medicalInfo: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true, profileMessage = null) }
            val res = authRepository.updateProfile(name, email, phone, medicalInfo)
            if (res is Result.Success) {
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        isEditingProfile = false,
                        profileMessage = "Profile updated successfully!"
                    )
                }
            } else if (res is Result.Error) {
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        profileMessage = "Failed to update profile: ${res.message}"
                    )
                }
            }
        }
    }

    fun clearProfileMessage() {
        _uiState.update { it.copy(profileMessage = null) }
    }

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

