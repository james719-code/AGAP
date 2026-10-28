package com.travelingsalesman.agap.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.travelingsalesman.agap.ui.screens.guest.GuestExperience
import com.travelingsalesman.agap.ui.screens.login.LoginScreen
import com.travelingsalesman.agap.ui.screens.register.RegisterScreen

/** Public browsing never creates account view models or observes private incident caches. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AgapNavGraph(
    startDestination: String = AgapDestinations.LOGIN_ROUTE,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    onSosClick: (() -> Unit)? = null,
    sessionViewModel: AppSessionViewModel = hiltViewModel()
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val darkMode by sessionViewModel.isDarkMode.collectAsStateWithLifecycle()
    val currentSession = session
    when {
        currentSession == null -> Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        currentSession.isAuthenticated -> {
            val destination = when (currentSession.role?.uppercase()) {
                "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                "RESPONDER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                else -> AgapDestinations.HOME_ROUTE
            }
            AuthenticatedNavGraph(
                startDestination = destination,
                modifier = modifier,
                onSosClick = onSosClick
            )
        }
        currentSession.isGuest -> GuestExperience(
            onSignIn = sessionViewModel::signIn,
            isDarkMode = darkMode,
            onDarkModeChange = sessionViewModel::setDarkMode,
            modifier = modifier
        )
        else -> NavHost(navController, startDestination = AgapDestinations.LOGIN_ROUTE, modifier = modifier) {
            composable(AgapDestinations.LOGIN_ROUTE) {
                LoginScreen(
                    onLoginSuccess = { /* The persisted session selects the authenticated graph. */ },
                    onNavigateToRegister = { navController.navigate(AgapDestinations.REGISTER_ROUTE) },
                    onContinueAsGuest = sessionViewModel::continueAsGuest
                )
            }
            composable(AgapDestinations.REGISTER_ROUTE) {
                RegisterScreen(
                    onRegisterSuccess = { navController.popBackStack() },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }
        }
    }
}
