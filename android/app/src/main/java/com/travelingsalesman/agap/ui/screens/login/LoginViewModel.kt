package com.travelingsalesman.agap.ui.screens.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.core.util.DeviceIdProvider
import com.travelingsalesman.agap.worker.SyncWorker
import com.travelingsalesman.agap.data.mock.OfflineTestDataSource
import com.travelingsalesman.agap.data.mock.LocalTestAccount

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccessRole: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val serverConfigManager: ServerConfigManager,
    private val deviceIdProvider: DeviceIdProvider,
    @ApplicationContext private val appContext: Context,
    offlineTestDataSource: OfflineTestDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    val localTestAccounts = offlineTestDataSource.accounts

    fun loginAsTestAccount(account: LocalTestAccount) {
        if (account !in localTestAccounts || _uiState.value.isLoading) return
        _uiState.update { it.copy(username = account.username, password = account.password) }
        login()
    }

    init {
        viewModelScope.launch {
            serverConfigManager.resetBleOnlyToAuto(reason = "login_screen")
        }
    }


    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username and password required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            serverConfigManager.resetBleOnlyToAuto(reason = "login_attempt")
            val result = loginUseCase(state.username, state.password, deviceIdProvider.getDeviceId())

            when (result) {
                is Result.Success -> {
                    // Login proves authenticated Internet is available now. Start
                    // the authoritative downlink immediately so the fixed reply
                    // catalog, incidents and routing metadata are cached before a
                    // later outage instead of waiting for the 15-minute worker.
                    SyncWorker.enqueueEventDrivenSync(appContext)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccessRole = result.data.user.role
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
