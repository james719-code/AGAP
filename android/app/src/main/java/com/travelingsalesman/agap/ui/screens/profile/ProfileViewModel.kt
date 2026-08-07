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

import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto

data class ProfileUiState(
    val username: String? = null,
    val role: String? = null,
    val userId: Long? = null,
    val agencyId: Long? = null,
    val agencyInfo: AgencyDto? = null,
    val agenciesList: List<AgencyDto> = emptyList(),
    val serverUrl: String = "",
    val pendingPacketCount: Int = 0,
    val isLoggedOut: Boolean = false,
    val feedbackMessage: String? = null,
    
    // Detailed Profile Data (Functional)
    val userProfile: UserProfileEntity? = null,
    val medicalInfo: MedicalInfoEntity? = null,
    val emergencyContacts: List<EmergencyContactDto> = emptyList(),
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
        fetchRemoteProfileAndContacts()
        fetchAgenciesList()
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
        viewModelScope.launch {
            preferencesManager.preferences.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        viewModelScope.launch {
            authTokenManager.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
    }

    fun fetchRemoteProfileAndContacts() {
        viewModelScope.launch {
            when (val userRes = authRepository.getProfile()) {
                is Result.Success -> {
                    val u = userRes.data
                    val profileEntity = UserProfileEntity(
                        userId = u.id,
                        fullName = u.name ?: u.username,
                        dob = u.birthday ?: "",
                        gender = u.gender ?: "",
                        phone = u.phoneNumber ?: "",
                        email = u.email ?: "",
                        address = "",
                        citizenId = u.displayId,
                        memberSince = u.createdAt ?: ""
                    )
                    val medicalEntity = MedicalInfoEntity(
                        userId = u.id,
                        bloodType = "O+",
                        allergies = "None",
                        conditions = u.medicalInfo ?: "None",
                        medications = "None",
                        emergencyNotes = "N/A"
                    )
                    _uiState.update {
                        it.copy(
                            userProfile = profileEntity,
                            medicalInfo = medicalEntity,
                            emergencyContacts = u.emergencyContacts
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun fetchAgenciesList() {
        viewModelScope.launch {
            when (val res = agencyRepository.getAgencies()) {
                is Result.Success -> {
                    _uiState.update { it.copy(agenciesList = res.data) }
                }
                else -> {}
            }
        }
    }

    fun createAgency(code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(
                code = code,
                name = name,
                type = type,
                contactNumber = contact
            )
            when (val res = agencyRepository.createAgency(dto)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency created successfully!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun updateAgency(id: Long, code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(
                id = id,
                code = code,
                name = name,
                type = type,
                contactNumber = contact
            )
            when (val res = agencyRepository.updateAgency(id, dto)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency updated successfully!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun deleteAgency(id: Long) {
        viewModelScope.launch {
            when (val res = agencyRepository.deleteAgency(id)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency deleted!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun addEmergencyContact(name: String, phone: String, relation: String, isPrimary: Boolean = false) {
        viewModelScope.launch {
            when (val res = authRepository.addEmergencyContact(name, phone, relation, isPrimary)) {
                is Result.Success -> {
                    fetchRemoteProfileAndContacts()
                    _uiState.update { it.copy(feedbackMessage = "Emergency contact added!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun updateEmergencyContact(contactId: Long, name: String, phone: String, relation: String, isPrimary: Boolean? = null) {
        viewModelScope.launch {
            when (val res = authRepository.updateEmergencyContact(contactId, name, phone, relation, isPrimary)) {
                is Result.Success -> {
                    fetchRemoteProfileAndContacts()
                    _uiState.update { it.copy(feedbackMessage = "Emergency contact updated!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun setPrimaryEmergencyContact(contactId: Long) {
        viewModelScope.launch {
            when (val res = authRepository.setPrimaryEmergencyContact(contactId)) {
                is Result.Success -> {
                    fetchRemoteProfileAndContacts()
                    _uiState.update { it.copy(feedbackMessage = "Primary emergency contact updated") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun deleteEmergencyContact(contactId: Long) {
        viewModelScope.launch {
            when (val res = authRepository.deleteEmergencyContact(contactId)) {
                is Result.Success -> {
                    fetchRemoteProfileAndContacts()
                    _uiState.update { it.copy(feedbackMessage = "Contact deleted") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${res.message}") }
                }
                else -> {}
            }
        }
    }

    fun updateProfile(name: String, email: String, phone: String, medicalInfo: String) {
        viewModelScope.launch {
            when (val res = authRepository.updateProfile(name, email, phone, medicalInfo)) {
                is Result.Success -> {
                    fetchRemoteProfileAndContacts()
                    _uiState.update { it.copy(feedbackMessage = "Profile updated successfully!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Update failed: ${res.message}") }
                }
                else -> {}
            }
        }
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

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }
}
