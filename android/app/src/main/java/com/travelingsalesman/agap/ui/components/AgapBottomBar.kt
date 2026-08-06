package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.theme.AGAPTheme
import com.travelingsalesman.agap.ui.theme.PaletteTextSecondaryDark
import com.travelingsalesman.agap.ui.theme.RedPrimary

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem(AgapDestinations.HOME_ROUTE, "Home", Icons.Outlined.Home)
    object Map : BottomNavItem(AgapDestinations.RESPONDER_MAP_ROUTE, "Map", Icons.Outlined.Map)
    object Alerts : BottomNavItem(AgapDestinations.SENT_MESSAGES_ROUTE, "Alerts", Icons.Outlined.Notifications)
    object Profile : BottomNavItem(AgapDestinations.PROFILE_ROUTE, "Profile", Icons.Outlined.Person)
}

@Composable
fun AgapBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSosClick: () -> Unit = {}
) {
    val leftItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Map
    )
    val rightItems = listOf(
        BottomNavItem.Alerts,
        BottomNavItem.Profile
    )

    val surfaceColor = Color(0xFF0F172A) // Dark Slate from mockup
    val selectedColor = RedPrimary
    val unselectedColor = PaletteTextSecondaryDark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp), // Floating offset
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main Bar Surface
        Surface(
            color = surfaceColor,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Items
                leftItems.forEach { item ->
                    BottomBarItem(
                        item = item,
                        isSelected = currentRoute == item.route,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Gap for SOS button
                Spacer(modifier = Modifier.width(72.dp))

                // Right Items
                rightItems.forEach { item ->
                    BottomBarItem(
                        item = item,
                        isSelected = currentRoute == item.route,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Floating SOS Button
        Box(
            modifier = Modifier
                .offset(y = (-20).dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)) // Darker ring around SOS
                .padding(4.dp)
                .clip(CircleShape)
                .background(RedPrimary)
                .clickable { onSosClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SOS",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = if (isSelected) selectedColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            color = if (isSelected) selectedColor else unselectedColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
