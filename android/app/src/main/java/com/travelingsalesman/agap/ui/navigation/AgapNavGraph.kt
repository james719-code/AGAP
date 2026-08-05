package com.travelingsalesman.agap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AgapDestinations.PROFILE_ROUTE) {
            ProfileScreen(
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
