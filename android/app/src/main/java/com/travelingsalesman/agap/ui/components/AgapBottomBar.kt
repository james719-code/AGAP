package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        AgapDestinations.HOME_ROUTE,
        "Home",
        Icons.Outlined.Home
    )

    object Map : BottomNavItem(
        AgapDestinations.CITIZEN_MAP_ROUTE,
        "Map",
        Icons.Outlined.Map
    )

    object ResponderMap : BottomNavItem(
        AgapDestinations.RESPONDER_MAP_ROUTE,
        "Dashboard",
        Icons.Outlined.Dashboard
    )

    object Alerts : BottomNavItem(
        AgapDestinations.SENT_MESSAGES_ROUTE,
        "Alerts",
        Icons.Outlined.Campaign
    )

    object Profile : BottomNavItem(
        AgapDestinations.PROFILE_ROUTE,
        "Profile",
        Icons.Outlined.Person
    )

    object Triage : BottomNavItem(
        AgapDestinations.ADMIN_MOBILE_ROUTE,
        "Triage",
        Icons.Outlined.Dashboard
    )

    object Incidents : BottomNavItem(
        AgapDestinations.INCIDENT_LIST_ROUTE,
        "Incidents",
        Icons.AutoMirrored.Outlined.Assignment
    )

    object Manage : BottomNavItem(
        AgapDestinations.ADMIN_MANAGEMENT_ROUTE,
        "Manage",
        Icons.Outlined.AdminPanelSettings
    )
}

/**
 * Main AGAP bottom navigation.
 *
 * IMPORTANT:
 * The center SOS control is NAVIGATION ONLY.
 *
 * Pressing it opens the SOS page. It does not create an incident, invoke SendSosUseCase,
 * start an alert, or trigger the MainActivity SOS callback.
 *
 * Actual SOS transmission is performed only from the drag-and-release joystick on HomeScreen.
 */
@Composable
fun AgapBottomBar(
    currentRoute: String?,
    userRole: String? = null,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSosClick: () -> Unit
) {
    val (leftItems, rightItems) = when (userRole?.uppercase()) {
        "ADMIN" -> Pair(
            listOf(BottomNavItem.Triage, BottomNavItem.Incidents),
            listOf(BottomNavItem.Manage, BottomNavItem.Profile)
        )

        "RESPONDER" -> Pair(
            listOf(BottomNavItem.ResponderMap, BottomNavItem.Incidents),
            listOf(BottomNavItem.Alerts, BottomNavItem.Profile)
        )

        else -> Pair(
            listOf(BottomNavItem.Home, BottomNavItem.Map),
            listOf(BottomNavItem.Alerts, BottomNavItem.Profile)
        )
    }

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isSosPageSelected = currentRoute == AgapDestinations.HOME_ROUTE
    val largeText = LocalDensity.current.fontScale > 1.3f

    fun isRouteSelected(itemRoute: String): Boolean {
        return when (itemRoute) {
            AgapDestinations.PROFILE_ROUTE -> {
                currentRoute == AgapDestinations.PROFILE_ROUTE ||
                        currentRoute == AgapDestinations.CITIZEN_PROFILE_ROUTE
            }

            // For citizen accounts the left "Home" item and the center SOS control point to
            // the same root page. Keep the visual emphasis on the dedicated center SOS control.
            AgapDestinations.HOME_ROUTE -> false

            else -> currentRoute == itemRoute
        }
    }

    fun getTitleOverride(item: BottomNavItem): String? {
        return if (userRole?.uppercase() == "RESPONDER" && item == BottomNavItem.Incidents) "Tasks" else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm)
            // Every destination, including SOS, is exposed as one tab in this navigation group.
            .selectableGroup(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = AgapShape.xxl,
            shadowElevation = 2.dp,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (largeText) 96.dp else 72.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                leftItems.forEach { item ->
                    BottomBarItem(
                        item = item,
                        isSelected = isRouteSelected(item.route),
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        titleOverride = getTitleOverride(item),
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Reserve one complete navigation slot for the centered SOS action so that
                // every visible destination keeps the same full-width touch target.
                Spacer(modifier = Modifier.weight(1f))

                rightItems.forEach { item ->
                    BottomBarItem(
                        item = item,
                        isSelected = isRouteSelected(item.route),
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        titleOverride = getTitleOverride(item),
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SosNavigationButton(
            selected = isSosPageSelected,
            onClick = onSosClick
        )
    }
}

@Composable
private fun SosNavigationButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(AgapShape.xl)
            .background(if (selected) primaryColor else MaterialTheme.colorScheme.primaryContainer)
            .semantics(mergeDescendants = true) {
                contentDescription = "SOS access"
                this.selected = selected
            }
            .clickable(
                role = Role.Tab,
                onClickLabel = "Open SOS access",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "SOS",
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    titleOverride: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemColor = if (isSelected) selectedColor else unselectedColor
    val displayTitle = titleOverride ?: item.title

    Box(
        modifier = modifier
            .fillMaxHeight()
            // The icon and text form one accessible destination instead of two focus targets.
            .semantics(mergeDescendants = true) {
                contentDescription = displayTitle
                selected = isSelected
            }
            .clickable(
                role = Role.Tab,
                onClickLabel = "Open $displayTitle",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(52.dp)
                    .clip(AgapShape.md)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = itemColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayTitle,
                color = itemColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
