package com.travelingsalesman.agap.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
    // The saved session loads in a few milliseconds. Show the plain themed
    // background meanwhile (a spinner here only flashes) and fade between states.
    // Each state renders with its own session, so the outgoing screen stays intact while it fades.
    AnimatedContent(
        targetState = session,
        contentKey = { s ->
            when {
                s == null -> "loading"
                s.isAuthenticated -> "auth:" + s.role?.uppercase()
                s.isGuest -> "guest"
                else -> "login"
            }
        },
        transitionSpec = {
            // The first screen after launch appears at once (the splash crossfade
            // already covers it); only later changes such as sign-in or sign-out fade.
            if (initialState == null) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(260)) togetherWith fadeOut(tween(180))
            }
        },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        label = "session_state"
    ) { currentSession ->
    when {
        currentSession == null -> Box(Modifier.fillMaxSize())
        currentSession.isAuthenticated -> {
            val destination = when (currentSession.role?.uppercase()) {
                "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                "RESPONDER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                else -> AgapDestinations.HOME_ROUTE
            }
            AuthenticatedNavGraph(
                startDestination = destination,
                modifier = Modifier.fillMaxSize(),
                onSosClick = onSosClick
            )
        }
        currentSession.isGuest -> GuestExperience(
            onSignIn = sessionViewModel::signIn,
            isDarkMode = darkMode,
            onDarkModeChange = sessionViewModel::setDarkMode,
            modifier = Modifier.fillMaxSize()
        )
        else -> NavHost(navController, startDestination = AgapDestinations.LOGIN_ROUTE, modifier = Modifier.fillMaxSize()) {
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
}
