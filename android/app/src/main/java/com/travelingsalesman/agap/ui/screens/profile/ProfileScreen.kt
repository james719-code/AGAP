package com.travelingsalesman.agap.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.navigation.AgapDestinations
import com.travelingsalesman.agap.ui.theme.GradientPrimaryRed
import com.travelingsalesman.agap.ui.theme.RedPrimary

import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onSwitchView: (String) -> Unit = {},
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "ACCOUNT PROFILE",
                showBackButton = true,
                onBackClick = onNavigateBack,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        },
        containerColor = bgColor
    ) { innerPadding ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Profile Avatar Halo Container
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(width = 2.dp, brush = GradientPrimaryRed, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = uiState.username ?: "User",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )

                // Role Pill Badge
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RedPrimary.copy(alpha = 0.15f))
                        .border(width = 1.dp, color = RedPrimary.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ROLE: ${uiState.role ?: "CITIZEN"}",
                        fontSize = 11.sp,
                        color = RedPrimary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Role View Mode Switcher Segmented Pill Control
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = RedPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SWITCH MOBILE ROLE INTERFACE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = RedPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Easily toggle between Citizen, Respondent, and Admin mobile views",
                            fontSize = 11.sp,
                            color = subTextColor,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // Segmented Row Picker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val roleOptions = listOf(
                                "CITIZEN" to AgapDestinations.HOME_ROUTE,
                                "RESPONDER" to AgapDestinations.RESPONDER_MAP_ROUTE,
                                "ADMIN" to AgapDestinations.ADMIN_MOBILE_ROUTE
                            )

                            roleOptions.forEach { (label, route) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cardBg)
                                        .clickable { onSwitchView(route) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // System Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = subTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYSTEM INFORMATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = subTextColor,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        ProfileRow("User ID", uiState.userId?.toString() ?: "N/A", textColor, subTextColor)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                        ProfileRow("Pending Offline Packets", uiState.pendingPacketCount.toString(), textColor, subTextColor)
                    }
                }

                // Agency Card (if applicable)
                uiState.agencyInfo?.let { agency ->
                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(width = 1.dp, color = borderCol, shape = RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = RedPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AFFILIATED AGENCY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RedPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            ProfileRow("Agency Code", agency.code, textColor, subTextColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                            ProfileRow("Agency Name", agency.name, textColor, subTextColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                            ProfileRow("Agency Type", agency.type, textColor, subTextColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderCol.copy(alpha = 0.5f))
                            ProfileRow("Contact Number", agency.contactNumber.ifEmpty { "N/A" }, textColor, subTextColor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logout Button
            Button(
                onClick = viewModel::logout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "LOGOUT ACCOUNT",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String, textColor: Color, subTextColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = subTextColor, fontSize = 12.sp)
        Text(text = value, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


