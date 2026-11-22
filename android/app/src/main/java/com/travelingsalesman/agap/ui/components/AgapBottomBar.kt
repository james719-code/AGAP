package com.travelingsalesman.agap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.navigation.AgapDestinations

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    /** Filled variant shown when the destination is active (Material 3 nav bar guidance). */
    val selectedIcon: ImageVector = icon
) {
    object Home : BottomNavItem(AgapDestinations.HOME_ROUTE, "Home", Icons.Outlined.Home, Icons.Filled.Home)
    object Map : BottomNavItem(AgapDestinations.CITIZEN_MAP_ROUTE, "Map", Icons.Outlined.Map, Icons.Filled.Map)
    object ResponderMap : BottomNavItem(AgapDestinations.RESPONDER_MAP_ROUTE, "Home", Icons.Outlined.Home, Icons.Filled.Home)
    object Alerts : BottomNavItem(AgapDestinations.SENT_MESSAGES_ROUTE, "Alerts", Icons.Outlined.Campaign, Icons.Filled.Campaign)
    object Profile : BottomNavItem(AgapDestinations.PROFILE_ROUTE, "Profile", Icons.Outlined.Person, Icons.Filled.Person)
    object Triage : BottomNavItem(AgapDestinations.ADMIN_MOBILE_ROUTE, "Triage", Icons.Outlined.Dashboard, Icons.Filled.Dashboard)
    object Incidents : BottomNavItem(AgapDestinations.INCIDENT_LIST_ROUTE, "Incidents", Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Filled.Assignment)
    object Manage : BottomNavItem(AgapDestinations.ADMIN_MANAGEMENT_ROUTE, "Manage", Icons.Outlined.AdminPanelSettings, Icons.Filled.AdminPanelSettings)
}

private val BarHeight = 72.dp
private val SosDiameter = 64.dp
/** How far the SOS button rises above the bar's top edge. */
private val SosRise = 28.dp
/** Gap between the SOS button and the edge of its cradle. */
private val CradleGap = 6.dp
private val BarCorner = 24.dp

/**
 * Bar outline with a circular cradle cut into the top edge, so the raised SOS
 * button reads as part of the bar instead of floating on top of it.
 */
private class CradleBarShape(
    private val cradleRadius: Dp,
    private val cradleCenterY: Dp,
    private val corner: Dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val bar = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        topLeft = CornerRadius(corner.toPx()),
                        topRight = CornerRadius(corner.toPx())
                    )
                )
            }
            val r = cradleRadius.toPx()
            val cx = size.width / 2f
            val cy = cradleCenterY.toPx()
            val cradle = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
            return Outline.Generic(Path().apply { op(bar, cradle, PathOperation.Difference) })
        }
    }
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

    val isSosPageSelected = currentRoute == AgapDestinations.HOME_ROUTE
    val largeText = LocalDensity.current.fontScale > 1.3f
    val barHeight = if (largeText) 92.dp else BarHeight

    fun isRouteSelected(itemRoute: String): Boolean = when (itemRoute) {
        AgapDestinations.PROFILE_ROUTE ->
            currentRoute == AgapDestinations.PROFILE_ROUTE || currentRoute == AgapDestinations.CITIZEN_PROFILE_ROUTE
        // For citizens, Home and the center SOS control share one page; the SOS control carries the emphasis.
        AgapDestinations.HOME_ROUTE -> false
        else -> currentRoute == itemRoute
    }

    fun titleFor(item: BottomNavItem): String =
        if (userRole?.uppercase() == "RESPONDER" && item == BottomNavItem.Incidents) "Tasks" else item.title

    val cradleRadius = SosDiameter / 2 + CradleGap
    // SOS centre measured from the top of the bar surface.
    val sosCenterFromBarTop = SosDiameter / 2 - SosRise
    val barShape = remember(cradleRadius, sosCenterFromBarTop) {
        CradleBarShape(cradleRadius, sosCenterFromBarTop, BarCorner)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SosRise),
            shape = barShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(barHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leftItems.forEach { item ->
                    BottomBarItem(item, isRouteSelected(item.route), titleFor(item), { onNavigate(item.route) }, Modifier.weight(1f))
                }
                // The centre slot belongs to SOS; its label sits under the raised button.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        "SOS",
                        modifier = Modifier.padding(bottom = 10.dp),
                        color = if (isSosPageSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                rightItems.forEach { item ->
                    BottomBarItem(item, isRouteSelected(item.route), titleFor(item), { onNavigate(item.route) }, Modifier.weight(1f))
                }
            }
        }

        SosNavigationButton(
            selected = isSosPageSelected,
            onClick = onSosClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun SosNavigationButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val red = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(SosDiameter)
            .shadow(elevation = if (selected) 12.dp else 8.dp, shape = CircleShape, ambientColor = red, spotColor = red)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(lerp(red, Color.White, 0.18f), red, lerp(red, Color.Black, 0.18f)))
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "SOS"
                this.selected = selected
            }
            .clickable(role = Role.Tab, onClickLabel = "Open SOS", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun BottomBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val indicatorColor by animateColorAsState(if (isSelected) colors.secondaryContainer else Color.Transparent, label = "nav_indicator")
    val iconColor by animateColorAsState(if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant, label = "nav_icon")
    val indicatorWidth by animateDpAsState(if (isSelected) 56.dp else 40.dp, label = "nav_indicator_width")
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                contentDescription = title
                selected = isSelected
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClickLabel = "Open $title",
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Material 3 active indicator: a pill behind the icon; the ripple is bounded to it.
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(indicatorWidth)
                .clip(CircleShape)
                .background(indicatorColor)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}
