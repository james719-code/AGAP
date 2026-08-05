package com.travelingsalesman.agap.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.travelingsalesman.agap.ui.components.AgapBottomBar
import com.travelingsalesman.agap.ui.screens.admin.AdminMobileScreen
import com.travelingsalesman.agap.ui.screens.home.HomeScreen
import com.travelingsalesman.agap.ui.screens.incidents.IncidentDetailScreen
import com.travelingsalesman.agap.ui.screens.incidents.IncidentListScreen
import com.travelingsalesman.agap.ui.screens.login.LoginScreen
import com.travelingsalesman.agap.ui.screens.profile.ProfileScreen
import com.travelingsalesman.agap.ui.screens.register.RegisterScreen
import com.travelingsalesman.agap.ui.screens.responder.ResponderMapScreen

object AgapDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val HOME_ROUTE = "home"
    const val RESPONDER_MAP_ROUTE = "responder_map"
    const val ADMIN_MOBILE_ROUTE = "admin_mobile"
    const val INCIDENT_LIST_ROUTE = "incident_list"
    const val INCIDENT_DETAIL_ROUTE = "incident_detail/{incidentId}"
    const val PROFILE_ROUTE = "profile"

    fun incidentDetail(incidentId: String) = "incident_detail/$incidentId"
}

@Composable
fun AgapNavGraph(
    startDestination: String = AgapDestinations.LOGIN_ROUTE,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        AgapDestinations.HOME_ROUTE,
        AgapDestinations.RESPONDER_MAP_ROUTE,
        AgapDestinations.INCIDENT_LIST_ROUTE,
        AgapDestinations.PROFILE_ROUTE,
        AgapDestinations.ADMIN_MOBILE_ROUTE
    )

    Scaffold(

        bottomBar = {
            if (showBottomBar) {
                AgapBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { targetRoute ->
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(AgapDestinations.HOME_ROUTE) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
        ) {

            composable(AgapDestinations.LOGIN_ROUTE) {
                LoginScreen(
                    onLoginSuccess = { role ->
                        val destination = when (role?.uppercase()) {
                            "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                            "RESPONDER", "DISPATCHER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                            else -> AgapDestinations.HOME_ROUTE
                        }
                        navController.navigate(destination) {
                            popUpTo(AgapDestinations.LOGIN_ROUTE) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(AgapDestinations.REGISTER_ROUTE)
                    }
                )
            }

            composable(AgapDestinations.REGISTER_ROUTE) {
                RegisterScreen(
                    onRegisterSuccess = { role ->
                        val destination = when (role?.uppercase()) {
                            "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                            "RESPONDER", "DISPATCHER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                            else -> AgapDestinations.HOME_ROUTE
                        }
                        navController.navigate(destination) {
                            popUpTo(AgapDestinations.LOGIN_ROUTE) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(AgapDestinations.LOGIN_ROUTE) {
                            popUpTo(AgapDestinations.REGISTER_ROUTE) { inclusive = true }
                        }
                    }
                )
            }

            composable(AgapDestinations.HOME_ROUTE) {
                HomeScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToIncidentList = {
                        navController.navigate(AgapDestinations.INCIDENT_LIST_ROUTE)
                    },
                    onNavigateToProfile = {
                        navController.navigate(AgapDestinations.PROFILE_ROUTE)
                    }
                )
            }

            composable(AgapDestinations.RESPONDER_MAP_ROUTE) {
                ResponderMapScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToIncidentList = {
                        navController.navigate(AgapDestinations.INCIDENT_LIST_ROUTE)
                    },
                    onNavigateToProfile = {
                        navController.navigate(AgapDestinations.PROFILE_ROUTE)
                    },
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(AgapDestinations.incidentDetail(incidentId))
                    }
                )
            }

            composable(AgapDestinations.ADMIN_MOBILE_ROUTE) {
                AdminMobileScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToIncidentList = {
                        navController.navigate(AgapDestinations.INCIDENT_LIST_ROUTE)
                    },
                    onNavigateToProfile = {
                        navController.navigate(AgapDestinations.PROFILE_ROUTE)
                    }
                )
            }

            composable(AgapDestinations.INCIDENT_LIST_ROUTE) {
                IncidentListScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(AgapDestinations.incidentDetail(incidentId))
                    }
                )
            }

            composable(
                route = AgapDestinations.INCIDENT_DETAIL_ROUTE,
                arguments = listOf(navArgument("incidentId") { type = NavType.StringType })
            ) {
                IncidentDetailScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(AgapDestinations.PROFILE_ROUTE) {
                ProfileScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateBack = { navController.popBackStack() },
                    onSwitchView = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(AgapDestinations.HOME_ROUTE) { inclusive = false }
                        }
                    },
                    onLogout = {
                        navController.navigate(AgapDestinations.LOGIN_ROUTE) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

        }
    }
}

