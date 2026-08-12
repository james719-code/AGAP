package com.travelingsalesman.agap.ui.screens.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.travelingsalesman.agap.core.util.DeviceIdProvider

import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.repository.AgencyRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository

data class RegisterUiState(
    val username: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val selectedRole: String = "CITIZEN",
    val agencyId: String = "",
    val selectedAgency: AgencyDto? = null,
    val availableAgencies: List<AgencyDto> = emptyList(),
    val selectedTeam: ResponderUnitDto? = null,
    val availableTeams: List<ResponderUnitDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccessRole: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val agencyRepository: AgencyRepository,
    private val responderRepository: ResponderRepository,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val defaultAgencies = listOf(
        AgencyDto(id = 101L, code = "CDRRMO-LEG", name = "Legazpi City DRRMO", type = "DRRMO"),
        AgencyDto(id = 102L, code = "BFP-LEGAZPI", name = "Bureau of Fire Protection - Legazpi Station", type = "FIRE"),
        AgencyDto(id = 103L, code = "PNP-LEG-CPS", name = "Legazpi City Police Station", type = "POLICE"),
        AgencyDto(id = 104L, code = "RED-CROSS-ALBAY", name = "Philippine Red Cross Albay Chapter", type = "MEDICAL")
    )

    init {
        fetchAgencies()
    }

    fun fetchAgencies() {
        viewModelScope.launch {
            when (val res = agencyRepository.getAgencies()) {
                is Result.Success -> {
                    val list = if (res.data.isNotEmpty()) res.data else defaultAgencies
                    val initialAgency = _uiState.value.selectedAgency ?: list.firstOrNull()
                    _uiState.update { state ->
                        state.copy(
                            availableAgencies = list,
                            selectedAgency = initialAgency
                        )
                    }
                    initialAgency?.let { fetchTeamsForAgency(it.id) }
                }
                else -> {
                    val initialAgency = _uiState.value.selectedAgency ?: defaultAgencies.firstOrNull()
                    _uiState.update { state ->
                        state.copy(
                            availableAgencies = defaultAgencies,
                            selectedAgency = initialAgency
                        )
                    }
                    initialAgency?.let { fetchTeamsForAgency(it.id) }
                }
            }
        }
    }

    fun fetchTeamsForAgency(agencyId: Long) {
        viewModelScope.launch {
            when (val res = responderRepository.getResponders(agencyId)) {
                is Result.Success -> {
                    val teams = res.data
                    _uiState.update { state ->
                        state.copy(
                            availableTeams = teams,
                            selectedTeam = teams.firstOrNull()
                        )
                    }
                }
                else -> {
                    _uiState.update { state ->
                        state.copy(
                            availableTeams = emptyList(),
                            selectedTeam = null
                        )
                    }
                }
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun onPhoneNumberChanged(value: String) {
        _uiState.update { it.copy(phoneNumber = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onRoleSelected(role: String) {
        if (role == "ADMIN") return // ADMIN registration is backend-only
        _uiState.update { it.copy(selectedRole = role, errorMessage = null) }
    }

    fun onAgencySelected(agency: AgencyDto) {
        _uiState.update {
            it.copy(
                selectedAgency = agency,
                agencyId = agency.id.toString(),
                errorMessage = null
            )
        }
        fetchTeamsForAgency(agency.id)
    }

    fun onTeamSelected(team: ResponderUnitDto) {
        _uiState.update {
            it.copy(
                selectedTeam = team,
                errorMessage = null
            )
        }
    }

    fun onAgencyIdChanged(value: String) {
        _uiState.update { it.copy(agencyId = value, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username and password required") }
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        val roleToSubmit = if (state.selectedRole == "ADMIN") "CITIZEN" else state.selectedRole
        val agencyIdLong = state.selectedAgency?.id ?: state.agencyId.toLongOrNull()
        val rescueUnitIdLong = if (roleToSubmit == "RESPONDER") state.selectedTeam?.id else null

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = registerUseCase(
                username = state.username,
                password = state.password,
                role = roleToSubmit,
                deviceId = deviceIdProvider.getDeviceId(),
                agencyId = agencyIdLong,
                rescueUnitId = rescueUnitIdLong,
                name = state.name.ifBlank { null },
                phoneNumber = state.phoneNumber.ifBlank { null }
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            registerSuccessRole = result.data.user.role
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

