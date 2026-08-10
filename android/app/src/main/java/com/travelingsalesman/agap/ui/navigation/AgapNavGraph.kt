package com.travelingsalesman.agap.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.travelingsalesman.agap.ui.components.AgapBottomBar
import com.travelingsalesman.agap.ui.screens.admin.AdminLiveMapScreen
import com.travelingsalesman.agap.ui.screens.admin.AdminMobileScreen
import com.travelingsalesman.agap.ui.screens.admin.AdminMobileViewModel
import com.travelingsalesman.agap.ui.screens.home.HomeScreen
import com.travelingsalesman.agap.ui.screens.incidents.IncidentDetailScreen
import com.travelingsalesman.agap.ui.screens.incidents.IncidentListScreen
import com.travelingsalesman.agap.ui.screens.login.LoginScreen
import com.travelingsalesman.agap.ui.screens.profile.ProfileScreen
import com.travelingsalesman.agap.ui.screens.register.RegisterScreen
import com.travelingsalesman.agap.ui.screens.responder.ResponderMapScreen
import com.travelingsalesman.agap.ui.screens.sent.SentMessagesScreen

object AgapDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"

    /**
     * HOME_ROUTE is the dedicated Citizen SOS page.
     * The center bottom-navigation SOS button navigates here only.
     */
    const val HOME_ROUTE = "home"

    const val RESPONDER_MAP_ROUTE = "responder_map"
    const val CITIZEN_MAP_ROUTE = "citizen_map"
    const val ADMIN_MOBILE_ROUTE = "admin_mobile"
    const val ADMIN_LIVE_MAP_ROUTE = "admin_live_map"
    const val ADMIN_MANAGEMENT_ROUTE = "admin_management"
    const val INCIDENT_LIST_ROUTE = "incident_list"
    const val INCIDENT_DETAIL_ROUTE = "incident_detail/{incidentId}"
    const val PROFILE_ROUTE = "profile"
    const val CITIZEN_PROFILE_ROUTE = "citizen_profile"
    const val SENT_MESSAGES_ROUTE = "sent_messages"
    const val OPERATIONAL_MESSAGES_ROUTE = "operational_messages"
    const val NOTIFICATIONS_ROUTE = "notifications"
    const val REPORT_FLOW_ROUTE = "report_flow"
    const val OFFLINE_MAP_PICKER_ROUTE = "offline_map_picker"

    fun incidentDetail(incidentId: String) = "incident_detail/$incidentId"
}

/**
 * [onSosClick] is kept only for source compatibility with MainActivity.
 *
 * It is intentionally NOT invoked from the center bottom-navigation SOS button anymore.
 * The center button now performs navigation only. Actual alerts are sent exclusively from
 * the drag-and-release EmergencySosJoystick inside HomeScreen.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AgapNavGraph(
    startDestination: String = AgapDestinations.LOGIN_ROUTE,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    onSosClick: (() -> Unit)? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val homeViewModel: com.travelingsalesman.agap.ui.screens.home.HomeViewModel =
        hiltViewModel()

    val profileViewModel: com.travelingsalesman.agap.ui.screens.profile.ProfileViewModel =
        hiltViewModel()

    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    val roleHomeRoute = when (profileUiState.role?.uppercase()) {
        "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
        "RESPONDER" -> AgapDestinations.RESPONDER_MAP_ROUTE
        else -> AgapDestinations.HOME_ROUTE
    }

    val isAlertsBottomTab = currentRoute == AgapDestinations.SENT_MESSAGES_ROUTE &&
            profileUiState.role?.uppercase() != "ADMIN"

    val showBottomBar = currentRoute in listOf(
        AgapDestinations.HOME_ROUTE,
        AgapDestinations.RESPONDER_MAP_ROUTE,
        AgapDestinations.CITIZEN_MAP_ROUTE,
        AgapDestinations.PROFILE_ROUTE,
        AgapDestinations.ADMIN_MOBILE_ROUTE,
        AgapDestinations.ADMIN_MANAGEMENT_ROUTE,
        AgapDestinations.INCIDENT_LIST_ROUTE
    ) || isAlertsBottomTab

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AgapBottomBar(
                    currentRoute = currentRoute,
                    userRole = profileUiState.role,
                    onNavigate = { targetRoute ->
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(roleHomeRoute) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },

                    // NAVIGATION ONLY.
                    // Do not call homeViewModel.triggerSos() here.
                    // Do not invoke the old MainActivity onSosClick callback here.
                    onSosClick = {
                        if (currentRoute != AgapDestinations.HOME_ROUTE) {
                            navController.navigate(AgapDestinations.HOME_ROUTE) {
                                // Keep the role's primary page underneath the SOS page so users
                                // can return to Triage/Responder Map without creating odd stacks.
                                popUpTo(roleHomeRoute) {
                                    saveState = true
                                }
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
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200)) { width -> width / 4 } },
            exitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { width -> -width / 4 } },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200)) { width -> -width / 4 } },
            popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { width -> width / 4 } }
        ) {
            composable(AgapDestinations.LOGIN_ROUTE) {
                LoginScreen(
                    onLoginSuccess = { role ->
                        val destination = when (role?.uppercase()) {
                            "ADMIN" -> AgapDestinations.ADMIN_MOBILE_ROUTE
                            "RESPONDER" -> AgapDestinations.RESPONDER_MAP_ROUTE
                            else -> AgapDestinations.HOME_ROUTE
                        }

                        navController.navigate(destination) {
                            popUpTo(AgapDestinations.LOGIN_ROUTE) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(AgapDestinations.REGISTER_ROUTE)
                    }
                )
            }

            composable(AgapDestinations.REGISTER_ROUTE) {
                RegisterScreen(
                    onRegisterSuccess = { _ ->
                        navController.navigate(AgapDestinations.LOGIN_ROUTE) {
                            popUpTo(AgapDestinations.REGISTER_ROUTE) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(AgapDestinations.LOGIN_ROUTE) {
                            popUpTo(AgapDestinations.REGISTER_ROUTE) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(AgapDestinations.HOME_ROUTE) {
                HomeScreen(
                    onNavigateToProfile = {
                        navController.navigate(AgapDestinations.PROFILE_ROUTE)
                    },
                    onNavigateToReport = {
                        navController.navigate(AgapDestinations.REPORT_FLOW_ROUTE)
                    },
                    onNavigateToNotifications = {
                        navController.navigate(AgapDestinations.SENT_MESSAGES_ROUTE)
                    },
                    viewModel = homeViewModel
                )
            }

            composable(AgapDestinations.REPORT_FLOW_ROUTE) {
                com.travelingsalesman.agap.ui.screens.report.ReportFlowScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToOfflineMapPicker = {
                        navController.navigate(
                            AgapDestinations.OFFLINE_MAP_PICKER_ROUTE
                        )
                    }
                )
            }

            composable(AgapDestinations.OFFLINE_MAP_PICKER_ROUTE) {
                val parentEntry = remember(it) {
                    navController.getBackStackEntry(
                        AgapDestinations.REPORT_FLOW_ROUTE
                    )
                }

                val reportViewModel:
                        com.travelingsalesman.agap.ui.screens.report.ReportViewModel =
                    hiltViewModel(parentEntry)

                val reportUiState by
                reportViewModel.uiState.collectAsStateWithLifecycle()

                com.travelingsalesman.agap.ui.map.OfflineLocationPicker(
                    initialLat = reportUiState.latitude ?: 13.1391,
                    initialLon = reportUiState.longitude ?: 123.7438,
                    onConfirmLocation = { lat, lon, src ->
                        reportViewModel.setLocationChoice(
                            src,
                            lat,
                            lon,
                            null,
                            null
                        )
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AgapDestinations.RESPONDER_MAP_ROUTE) {
                ResponderMapScreen(
                    onNavigateToProfile = {
                        navController.navigate(
                            AgapDestinations.PROFILE_ROUTE
                        )
                    },
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    },
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.OPERATIONAL_MESSAGES_ROUTE
                        )
                    }
                )
            }

            composable(AgapDestinations.CITIZEN_MAP_ROUTE) {
                com.travelingsalesman.agap.ui.screens.map.CitizenMapScreen(
                    onNavigateToProfile = {
                        navController.navigate(
                            AgapDestinations.PROFILE_ROUTE
                        )
                    },
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.SENT_MESSAGES_ROUTE
                        )
                    },
                    viewModel = homeViewModel
                )
            }

            composable(AgapDestinations.ADMIN_MOBILE_ROUTE) { backStackEntry ->
                val adminViewModel: AdminMobileViewModel =
                    hiltViewModel(backStackEntry)

                AdminMobileScreen(
                    onNavigateToProfile = {
                        navController.navigate(
                            AgapDestinations.PROFILE_ROUTE
                        )
                    },
                    onOpenFullMap = {
                        navController.navigate(
                            AgapDestinations.ADMIN_LIVE_MAP_ROUTE
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToIncidents = {
                        navController.navigate(
                            AgapDestinations.INCIDENT_LIST_ROUTE
                        ) {
                            popUpTo(roleHomeRoute) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToIncidentDetail = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    },
                    onNavigateToManagement = {
                        navController.navigate(
                            AgapDestinations.ADMIN_MANAGEMENT_ROUTE
                        ) {
                            popUpTo(roleHomeRoute) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.OPERATIONAL_MESSAGES_ROUTE
                        )
                    },
                    viewModel = adminViewModel
                )
            }

            /*
             * Full-screen operational map is intentionally NOT part of showBottomBar.
             * It gets the whole screen and reuses the Admin Mobile route's ViewModel,
             * so opening the map does not create another heavy incident/responder state tree.
             */
            composable(
                route = AgapDestinations.ADMIN_LIVE_MAP_ROUTE,
                enterTransition = { fadeIn(tween(160)) + slideInHorizontally(tween(280)) { width -> -width } },
                exitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { width -> -width / 4 } },
                popEnterTransition = { fadeIn(tween(140)) + slideInHorizontally(tween(220)) { width -> -width / 4 } },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { width -> width } }
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(
                        AgapDestinations.ADMIN_MOBILE_ROUTE
                    )
                }

                val adminViewModel: AdminMobileViewModel =
                    hiltViewModel(parentEntry)

                AdminLiveMapScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToIncident = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(
                                incidentId
                            )
                        )
                    },
                    viewModel = adminViewModel
                )
            }

            composable(AgapDestinations.ADMIN_MANAGEMENT_ROUTE) {
                val isOperationalRole = profileUiState.role?.uppercase() in listOf("ADMIN", "RESPONDER")

                com.travelingsalesman.agap.ui.screens.admin.AdminManagementScreen(
                    showBackButton = false,
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.SENT_MESSAGES_ROUTE
                        )
                    },
                    onNavigateToMessages = if (isOperationalRole) {
                        { navController.navigate(AgapDestinations.OPERATIONAL_MESSAGES_ROUTE) }
                    } else null
                )
            }

            composable(AgapDestinations.INCIDENT_LIST_ROUTE) {
                val isBottomTab =
                    profileUiState.role?.uppercase() in listOf(
                        "ADMIN",
                        "RESPONDER"
                    )
                val isOperationalRole = profileUiState.role?.uppercase() in listOf("ADMIN", "RESPONDER")

                IncidentListScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.SENT_MESSAGES_ROUTE
                        )
                    },
                    onNavigateToMessages = if (isOperationalRole) {
                        { navController.navigate(AgapDestinations.OPERATIONAL_MESSAGES_ROUTE) }
                    } else null,
                    showBackButton = !isBottomTab,
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    }
                )
            }

            composable(
                route = AgapDestinations.INCIDENT_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("incidentId") {
                        type = NavType.StringType
                    }
                )
            ) {
                IncidentDetailScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(AgapDestinations.PROFILE_ROUTE) {
                val isOperationalRole = profileUiState.role?.uppercase() in listOf("ADMIN", "RESPONDER")

                ProfileScreen(
                    showBackButton = false,
                    onNavigateToNotifications = {
                        navController.navigate(
                            AgapDestinations.SENT_MESSAGES_ROUTE
                        )
                    },
                    onNavigateToMessages = if (isOperationalRole) {
                        { navController.navigate(AgapDestinations.OPERATIONAL_MESSAGES_ROUTE) }
                    } else null,
                    onSwitchView = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(AgapDestinations.HOME_ROUTE) {
                                inclusive = false
                            }
                        }
                    },
                    onLogout = {
                        navController.navigate(
                            AgapDestinations.LOGIN_ROUTE
                        ) {
                            popUpTo(0) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToDetailedProfile = {
                        navController.navigate(
                            AgapDestinations.CITIZEN_PROFILE_ROUTE
                        )
                    }
                )
            }

            composable(AgapDestinations.CITIZEN_PROFILE_ROUTE) {
                com.travelingsalesman.agap.ui.screens.profile.CitizenProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onLogout = {
                        navController.navigate(
                            AgapDestinations.LOGIN_ROUTE
                        ) {
                            popUpTo(0) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(AgapDestinations.SENT_MESSAGES_ROUTE) {
                val isBottomTab = profileUiState.role?.uppercase() != "ADMIN"

                SentMessagesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    },
                    showBackButton = !isBottomTab
                )
            }

            composable(AgapDestinations.OPERATIONAL_MESSAGES_ROUTE) {
                com.travelingsalesman.agap.ui.screens.messages.OperationalMessagesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToDetail = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    },
                    onNavigateToIncidents = {
                        navController.navigate(
                            AgapDestinations.INCIDENT_LIST_ROUTE
                        )
                    },
                    showBackButton = true
                )
            }

            composable(AgapDestinations.NOTIFICATIONS_ROUTE) {
                com.travelingsalesman.agap.ui.screens.notifications.NotificationsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToIncident = { incidentId ->
                        navController.navigate(
                            AgapDestinations.incidentDetail(incidentId)
                        )
                    }
                )
            }
        }
    }
}
