package com.travelingsalesman.agap.ui.components

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.theme.RedPrimary

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem(AgapDestinations.HOME_ROUTE, "Home", Icons.Default.Home)
    object Map : BottomNavItem(AgapDestinations.RESPONDER_MAP_ROUTE, "Map", Icons.Default.Map)
    object Incidents : BottomNavItem(AgapDestinations.INCIDENT_LIST_ROUTE, "Alerts", Icons.AutoMirrored.Filled.List)
    object Profile : BottomNavItem(AgapDestinations.PROFILE_ROUTE, "Profile", Icons.Default.Person)
}

@Composable

fun AgapBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Map,
        BottomNavItem.Incidents,
        BottomNavItem.Profile
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = RedPrimary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isSelected) Modifier.background(selectedColor.copy(alpha = 0.15f))
                            else Modifier
                        )
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) selectedColor else unselectedColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.title,
                            color = if (isSelected) selectedColor else unselectedColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}


