package com.travelingsalesman.agap.ui.components

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.R
import com.travelingsalesman.agap.core.bluetooth.BleMeshState
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.RedPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgapTopBar(
    title: String = "AGAP DISPATCH",
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    onThemeToggle: (() -> Unit)? = null,
    onNotificationClick: () -> Unit = {},
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

    CenterAlignedTopAppBar(
        title = {
            if (showBackButton) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.logo_name_nobg),
                    contentDescription = "AGAP Logo",
                    modifier = Modifier.height(56.dp)
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
                IconButton(onClick = {
                    scope.launch {
                        authTokenManager.setDarkMode(!isDarkModeState)
                    }
                    onThemeToggle?.invoke()
                }) {
                    Icon(
                        imageVector = if (isDarkModeState) Icons.Default.WbSunny else Icons.Default.DarkMode,
                        contentDescription = if (isDarkModeState) "Switch to Light Mode" else "Switch to Dark Mode",
                        tint = if (isDarkModeState) Color(0xFFFFB300) else textColor
                    )
                }
            }
        },
        actions = {
            val isBluetoothOn = remember(bleMeshState) {
                if (bleMeshState?.isEnabled == true) {
                    true
                } else {
                    try {
                        val adapter = BluetoothAdapter.getDefaultAdapter()
                        adapter?.isEnabled == true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            // Bluetooth Mesh Status Icon
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = if (isBluetoothOn) BlueMedical else textColor.copy(alpha = 0.40f)
                )
            }

            if (pendingPacketCount > 0) {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Pending Packets",
                            tint = Color(0xFFFFA000)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFA000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pendingPacketCount.toString(),
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Notification Bell with Badge
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = textColor
                    )
                }
                if (notificationCount > 0) {
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
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor
        )
    )
}
