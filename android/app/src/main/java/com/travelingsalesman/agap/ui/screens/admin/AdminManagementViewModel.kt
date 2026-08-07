package com.travelingsalesman.agap.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import com.travelingsalesman.agap.data.repository.AgencyRepository
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminManagementUiState(
    val selectedTab: Int = 0, // 0: User Verification, 1: Agencies, 2: Responder Units
    val usersList: List<UserDto> = emptyList(),
    val agenciesList: List<AgencyDto> = emptyList(),
    val responderUnitsList: List<ResponderUnitDto> = emptyList(),
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class AdminManagementViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val responderRepository: ResponderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminManagementUiState())
    val uiState: StateFlow<AdminManagementUiState> = _uiState.asStateFlow()

    init {
        refreshAllData()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            fetchUsers()
            fetchAgencies()
            fetchResponders()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // --- User Verification Queue ---
    fun fetchUsers() {
        viewModelScope.launch {
            when (val res = authRepository.listUsers()) {
                is Result.Success -> _uiState.update { it.copy(usersList = res.data) }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun verifyUserAccount(userId: Long) {
        viewModelScope.launch {
            when (val res = authRepository.verifyUser(userId)) {
                is Result.Success -> {
                    fetchUsers()
                    _uiState.update { it.copy(feedbackMessage = "User account verified!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun deleteUserAccount(userId: Long) {
        viewModelScope.launch {
            when (val res = authRepository.deleteUser(userId)) {
                is Result.Success -> {
                    fetchUsers()
                    _uiState.update { it.copy(feedbackMessage = "User account removed") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    // --- Agencies CRUD ---
    fun fetchAgencies() {
        viewModelScope.launch {
            when (val res = agencyRepository.getAgencies()) {
                is Result.Success -> _uiState.update { it.copy(agenciesList = res.data) }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun createAgency(code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(code = code, name = name, type = type, contactNumber = contact)
            when (val res = agencyRepository.createAgency(dto)) {
                is Result.Success -> {
                    fetchAgencies()
                    _uiState.update { it.copy(feedbackMessage = "Agency created!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun updateAgency(id: Long, code: String, name: String, type: String, contact: String) {
        viewModelScope.launch {
            val dto = AgencyDto(id = id, code = code, name = name, type = type, contactNumber = contact)
            when (val res = agencyRepository.updateAgency(id, dto)) {
                is Result.Success -> {
                    fetchAgencies()
                    _uiState.update { it.copy(feedbackMessage = "Agency updated!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun deleteAgency(id: Long) {
        viewModelScope.launch {
            when (val res = agencyRepository.deleteAgency(id)) {
                is Result.Success -> {
                    fetchAgencies()
                    _uiState.update { it.copy(feedbackMessage = "Agency deleted!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    // --- Responder Units CRUD ---
    fun fetchResponders() {
        viewModelScope.launch {
            when (val res = responderRepository.getResponders()) {
                is Result.Success -> _uiState.update { it.copy(responderUnitsList = res.data) }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun createResponderUnit(agencyId: Long, unitCallsign: String, responderName: String, phone: String, targetId: String) {
        viewModelScope.launch {
            val dto = ResponderUnitDto(
                agencyId = agencyId,
                unitCallsign = unitCallsign,
                targetId = if (targetId.isNotBlank()) targetId else "TARGET-$unitCallsign",
                status = "IDLE"
            )
            when (val res = responderRepository.createResponderUnit(dto)) {
                is Result.Success -> {
                    fetchResponders()
                    _uiState.update { it.copy(feedbackMessage = "Responder Unit created!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun updateResponderUnit(id: Long, agencyId: Long, unitCallsign: String, status: String, targetId: String) {
        viewModelScope.launch {
            val dto = ResponderUnitDto(
                id = id,
                agencyId = agencyId,
                unitCallsign = unitCallsign,
                targetId = targetId,
                status = status
            )
            when (val res = responderRepository.updateResponderUnit(id, dto)) {
                is Result.Success -> {
                    fetchResponders()
                    _uiState.update { it.copy(feedbackMessage = "Responder Unit updated!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun deleteResponderUnit(id: Long) {
        viewModelScope.launch {
            when (val res = responderRepository.deleteResponderUnit(id)) {
                is Result.Success -> {
                    fetchResponders()
                    _uiState.update { it.copy(feedbackMessage = "Responder Unit deleted!") }
                }
                is Result.Error -> _uiState.update { it.copy(feedbackMessage = res.message) }
                else -> {}
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
