package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgapTopBar(
    title: String = "AGAP DISPATCH",
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null,
    notificationCount: Int = 0,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    letterSpacing = 1.0.sp
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GreenOnline)
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "AGAP Logo",
                        tint = RedPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        actions = {
            // Theme Toggle Button
            onToggleTheme?.let { toggle ->
                IconButton(onClick = toggle) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Light/Dark Theme",
                        tint = textColor
                    )
                }
            }

            // Notification Bell with Badge
            if (notificationCount > 0) {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = textColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(RedPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notificationCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            actions()

            onProfileClick?.let { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = textColor
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor
        )
    )
}


