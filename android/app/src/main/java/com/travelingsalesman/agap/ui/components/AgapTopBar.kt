package com.travelingsalesman.agap.ui.components

import android.bluetooth.BluetoothManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AgapStatus
import com.travelingsalesman.agap.ui.theme.GreenOnline
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgapTopBar(
    title: String = "Emergency assistance",
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    onThemeToggle: (() -> Unit)? = null,
    onNotificationClick: () -> Unit = {},
    onMessageClick: (() -> Unit)? = null,
    notificationCount: Int = 0,
    pendingPacketCount: Int = 0,
    bleMeshState: BleMeshState? = null,
    showStatusDot: Boolean = false,
    statusDotColor: Color = GreenOnline,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authTokenManager = remember(context) { AuthTokenManager(context) }
    val isDarkModeState by authTokenManager.isDarkMode.collectAsStateWithLifecycle(
        initialValue = isSystemInDarkTheme()
    )

    val containerColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val normalizedTitle = if (title == title.uppercase()) {
        title.lowercase().replaceFirstChar { it.titlecase() }
            .replace("agap", "AGAP").replace("sos", "SOS")
    } else title
    val displayTitle = if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) "Offline test · $normalizedTitle" else normalizedTitle

    TopAppBar(
        title = {
            if (showBackButton) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
            } else {
                AgapBrand(
                    compact = true,
                    subtitle = displayTitle,
                    modifier = Modifier
                        .then(
                            if (onProfileClick != null) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClickLabel = "Open profile",
                                    onClick = onProfileClick
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }
            }
        },
        actions = {
            if (!showBackButton) {
                IconButton(onClick = {
                    scope.launch {
                        authTokenManager.setDarkMode(!isDarkModeState)
                    }
                    onThemeToggle?.invoke()
                }) {
                    Icon(
                        imageVector = if (isDarkModeState) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                        contentDescription = if (isDarkModeState) "Switch to Light Mode" else "Switch to Dark Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val isBluetoothOn = remember(bleMeshState) {
                if (bleMeshState?.isEnabled == true) {
                    true
                } else {
                    try {
                        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                        adapter?.isEnabled == true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            // Bluetooth status is informational. Do not expose a dead click action to assistive tech.
            if (!showBackButton && bleMeshState != null) Box(
                modifier = Modifier
                    .size(32.dp)
                    .semantics {
                        contentDescription = "Bluetooth mesh"
                        stateDescription = if (isBluetoothOn) "Available" else "Unavailable"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    tint = if (isBluetoothOn) AgapStatus.info else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onMessageClick != null) {
                IconButton(onClick = onMessageClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Operational Broadcast & Messaging",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Notification Bell with Badge
            val notificationDescription = when (notificationCount) {
                0 -> "Notifications, 0 unread notifications"
                1 -> "Notifications, 1 unread notification"
                else -> "Notifications, $notificationCount unread notifications"
            }

            IconButton(onClick = onNotificationClick) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = notificationDescription,
                        tint = textColor
                    )
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = AgapSpacing.sm, end = AgapSpacing.sm)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clearAndSetSemantics {},
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (notificationCount > 9) "9+" else notificationCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor
        )
    )
}
