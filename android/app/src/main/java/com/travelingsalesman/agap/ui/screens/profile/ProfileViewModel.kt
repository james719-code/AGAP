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

data class ProfileUiState(
    val username: String? = null,
    val role: String? = null,
    val userId: Long? = null,
    val agencyId: Long? = null,
    val agencyInfo: AgencyDto? = null,
    val serverUrl: String = "",
    val pendingPacketCount: Int = 0,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val agencyRepository: AgencyRepository,
    private val telemetryRepository: TelemetryRepository,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
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
