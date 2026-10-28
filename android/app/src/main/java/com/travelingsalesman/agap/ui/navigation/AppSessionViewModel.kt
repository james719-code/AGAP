package com.travelingsalesman.agap.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.data.local.AuthTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authTokenManager: AuthTokenManager
) : ViewModel() {
    val session = authTokenManager.appSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isDarkMode = authTokenManager.isDarkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun continueAsGuest() { viewModelScope.launch { authTokenManager.enterGuestMode() } }
    fun signIn() { viewModelScope.launch { authTokenManager.leaveGuestMode() } }
    fun setDarkMode(enabled: Boolean) { viewModelScope.launch { authTokenManager.setDarkMode(enabled) } }
}
