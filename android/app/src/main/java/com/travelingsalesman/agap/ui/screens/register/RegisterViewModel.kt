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

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val selectedRole: String = "CITIZEN",
    val agencyId: String = "",
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registerSuccessRole: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val serverConfigManager: ServerConfigManager,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverConfigManager.serverUrl.collect { url ->
                _uiState.update { it.copy(serverUrl = url) }
            }
        }
    }

    fun onServerUrlChanged(newUrl: String) {
        _uiState.update { it.copy(serverUrl = newUrl) }
        viewModelScope.launch {
            serverConfigManager.setServerUrl(newUrl)
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun onRoleSelected(role: String) {
        _uiState.update { it.copy(selectedRole = role, errorMessage = null) }
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

        val agencyIdLong = state.agencyId.toLongOrNull()

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = registerUseCase(
                username = state.username,
                password = state.password,
                role = state.selectedRole,
                deviceId = deviceIdProvider.getDeviceId(),
                agencyId = agencyIdLong
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

