package com.travelingsalesman.agap.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.bluetooth.BleRelayScheduler
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.util.UserMessageResolver
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.EmergencyPreferences
import com.travelingsalesman.agap.data.local.EmergencyPreferencesManager
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.entity.MedicalInfoEntity
import com.travelingsalesman.agap.data.local.entity.UserProfileEntity
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.EmergencyContactDto
import com.travelingsalesman.agap.data.repository.AgencyRepository
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    // Account/profile state returned by the API.
    val accountStatus: String? = null,
    val isVerified: Boolean = false,
    val lastLoginAt: String? = null,
    val isRefreshing: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingContact: Boolean = false,

    // Detailed profile data.
    val userProfile: UserProfileEntity? = null,
    val medicalInfo: MedicalInfoEntity? = null,
    val emergencyContacts: List<EmergencyContactDto> = emptyList(),
    val preferences: EmergencyPreferences = EmergencyPreferences(),
    val isDarkMode: Boolean = true
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val telemetryRepository: TelemetryRepository,
    private val relayScheduler: BleRelayScheduler,
    private val serverConfigManager: ServerConfigManager,
    @Suppress("unused") private val userProfileRepository: UserProfileRepository,
    private val preferencesManager: EmergencyPreferencesManager,
    private val authTokenManager: AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeBaseData()
        observeCachedProfile()
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
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(role = role) }
            }
        }

        viewModelScope.launch {
            authRepository.userId.collect { userId ->
                _uiState.update { it.copy(userId = userId) }
            }
        }

        viewModelScope.launch {
            authRepository.agencyId.collect { agencyId ->
                _uiState.update { it.copy(agencyId = agencyId) }
                if (agencyId != null) fetchAgency(agencyId)
            }
        }

        viewModelScope.launch {
            combine(
                telemetryRepository.getPendingCount(),
                relayScheduler.observeQueuedRelayCount()
            ) { cloudPending, meshPending ->
                cloudPending + meshPending
            }.distinctUntilChanged().collect { count ->
                _uiState.update { it.copy(pendingPacketCount = count) }
            }
        }

        viewModelScope.launch {
            preferencesManager.preferences.collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
            }
        }

        viewModelScope.launch {
            authTokenManager.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
    }

    private fun observeCachedProfile() {
        viewModelScope.launch {
            authRepository.userId
                .filterNotNull()
                .flatMapLatest { userId ->
                    combine(
                        userProfileRepository.getUserProfile(userId),
                        userProfileRepository.getMedicalInfo(userId),
                        userProfileRepository.getEmergencyContacts(userId)
                    ) { profile, medical, contacts -> Triple(profile, medical, contacts) }
                }
                .collect { (profile, medical, contacts) ->
                    _uiState.update { state ->
                        state.copy(
                            userProfile = profile,
                            medicalInfo = medical,
                            emergencyContacts = contacts.map {
                                EmergencyContactDto(
                                    id = it.contactId,
                                    userId = it.userId,
                                    contactName = it.name,
                                    phoneNumber = it.phone,
                                    relationship = it.relationship,
                                    isPrimary = it.isPrimary
                                )
                            }
                        )
                    }
                }
        }
    }

    /**
     * Keeps the original public method so existing call sites continue to compile.
     */
    fun fetchRemoteProfileAndContacts() {
        viewModelScope.launch {
            loadRemoteProfile(showSuccessMessage = false)
        }
    }

    /**
     * Explicit refresh used by the detailed profile screen.
     */
    fun refreshProfile() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            loadRemoteProfile(showSuccessMessage = true)
        }
    }

    private suspend fun loadRemoteProfile(showSuccessMessage: Boolean) {
        _uiState.update { it.copy(isRefreshing = true) }

        when (val userRes = authRepository.getProfile()) {
            is Result.Success -> {
                val user = userRes.data

                val profileEntity = UserProfileEntity(
                    userId = user.id,
                    fullName = user.name ?: user.username,
                    dob = user.birthday.orEmpty(),
                    gender = user.gender.orEmpty(),
                    phone = user.phoneNumber.orEmpty(),
                    email = user.email.orEmpty(),
                    address = "",
                    citizenId = user.displayId,
                    memberSince = user.createdAt.orEmpty()
                )

                // The current API exposes one free-form medical_info field. Do not invent a
                // blood type/allergy value when the server did not return one.
                val medicalEntity = MedicalInfoEntity(
                    userId = user.id,
                    bloodType = "",
                    allergies = "",
                    conditions = user.medicalInfo.orEmpty(),
                    medications = "",
                    emergencyNotes = ""
                )

                userProfileRepository.saveUserSnapshot(user)

                _uiState.update {
                    it.copy(
                        userProfile = profileEntity,
                        medicalInfo = medicalEntity,
                        emergencyContacts = user.emergencyContacts,
                        accountStatus = user.accountStatus,
                        isVerified = user.isVerified,
                        lastLoginAt = user.lastLoginAt,
                        isRefreshing = false,
                        feedbackMessage = if (showSuccessMessage) {
                            "Profile synchronized"
                        } else {
                            it.feedbackMessage
                        }
                    )
                }
            }

            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        feedbackMessage = UserMessageResolver.safe(
                            userRes.message,
                            "Your saved profile is available, but the latest account details could not be loaded."
                        )
                    )
                }
            }

            Result.Loading -> {
                _uiState.update { it.copy(isRefreshing = true) }
            }
        }
    }

    fun fetchAgenciesList() {
        viewModelScope.launch {
            when (val result = agencyRepository.getAgencies()) {
                is Result.Success -> {
                    _uiState.update { it.copy(agenciesList = result.data) }
                }

                is Result.Error -> Unit
                Result.Loading -> Unit
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

            when (val result = agencyRepository.createAgency(dto)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency created successfully") }
                }

                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${result.message}") }
                }

                Result.Loading -> Unit
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

            when (val result = agencyRepository.updateAgency(id, dto)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency updated successfully") }
                }

                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${result.message}") }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun deleteAgency(id: Long) {
        viewModelScope.launch {
            when (val result = agencyRepository.deleteAgency(id)) {
                is Result.Success -> {
                    fetchAgenciesList()
                    _uiState.update { it.copy(feedbackMessage = "Agency deleted") }
                }

                is Result.Error -> {
                    _uiState.update { it.copy(feedbackMessage = "Failed: ${result.message}") }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun addEmergencyContact(
        name: String,
        phone: String,
        relation: String,
        isPrimary: Boolean = false
    ) {
        if (_uiState.value.isSavingContact) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingContact = true) }

            when (
                val result = authRepository.addEmergencyContact(
                    name = name.trim(),
                    phone = phone.trim(),
                    relation = relation.trim(),
                    isPrimary = isPrimary
                )
            ) {
                is Result.Success -> {
                    loadRemoteProfile(showSuccessMessage = false)
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Emergency contact added"
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun updateEmergencyContact(
        contactId: Long,
        name: String,
        phone: String,
        relation: String,
        isPrimary: Boolean? = null
    ) {
        if (_uiState.value.isSavingContact) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingContact = true) }

            when (
                val result = authRepository.updateEmergencyContact(
                    contactId = contactId,
                    name = name.trim(),
                    phone = phone.trim(),
                    relation = relation.trim(),
                    isPrimary = isPrimary
                )
            ) {
                is Result.Success -> {
                    loadRemoteProfile(showSuccessMessage = false)
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Emergency contact updated"
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun setPrimaryEmergencyContact(contactId: Long) {
        if (_uiState.value.isSavingContact) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingContact = true) }

            when (val result = authRepository.setPrimaryEmergencyContact(contactId)) {
                is Result.Success -> {
                    loadRemoteProfile(showSuccessMessage = false)
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Primary emergency contact updated"
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun deleteEmergencyContact(contactId: Long) {
        if (_uiState.value.isSavingContact) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingContact = true) }

            when (val result = authRepository.deleteEmergencyContact(contactId)) {
                is Result.Success -> {
                    loadRemoteProfile(showSuccessMessage = false)
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Emergency contact removed"
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
                            feedbackMessage = "Failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun updateProfile(name: String, email: String, phone: String, medicalInfo: String) {
        if (_uiState.value.isSavingProfile) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }

            when (
                val result = authRepository.updateProfile(
                    name = name.trim(),
                    email = email.trim(),
                    phone = phone.trim(),
                    medicalInfo = medicalInfo.trim()
                )
            ) {
                is Result.Success -> {
                    loadRemoteProfile(showSuccessMessage = false)
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            feedbackMessage = "Profile updated successfully"
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            feedbackMessage = "Update failed: ${result.message}"
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun updateAutoSendGps(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.updateAutoSendGps(enabled)
    }

    fun updateBleFallback(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.updateBleFallback(enabled)
    }

    fun updateVibrationAlert(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.updateVibrationAlert(enabled)
    }

    fun updateSilentMode(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.updateSilentMode(enabled)
    }

    fun updateOfflineSharing(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.updateOfflineSharing(enabled)
    }

    fun updateDarkMode(enabled: Boolean) = viewModelScope.launch {
        authTokenManager.setDarkMode(enabled)
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
