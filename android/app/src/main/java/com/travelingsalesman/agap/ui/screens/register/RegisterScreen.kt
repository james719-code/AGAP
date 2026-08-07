package com.travelingsalesman.agap.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCardBackground
import com.travelingsalesman.agap.ui.theme.glassCardBorder

@Composable
fun RegisterScreen(
    onRegisterSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = glassCardBackground()
    val borderCol = glassCardBorder()
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(uiState.registerSuccessRole) {
        uiState.registerSuccessRole?.let { role ->
            onRegisterSuccess(role)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(AgapSpacing.xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .clip(AgapShape.xxl)
                .background(cardBg)
                .border(width = 1.dp, color = borderCol, shape = AgapShape.xxl)
                .padding(AgapSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "AGAP",
                tint = RedPrimary,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            Text(
                text = "CREATE AGAP ACCOUNT",
                style = MaterialTheme.typography.headlineMedium,
                color = textColor,
                letterSpacing = 1.sp
            )

            Text(
                text = "Register for Citizen SOS or Responder Access",
                style = MaterialTheme.typography.bodySmall,
                color = subTextColor
            )

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            // Role selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val isCitizen = uiState.selectedRole == "CITIZEN"
                FilterChip(
                    selected = isCitizen,
                    onClick = { viewModel.onRoleSelected("CITIZEN") },
                    label = { Text("CITIZEN", style = MaterialTheme.typography.labelSmall) },
                    shape = AgapShape.sm,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = bgColor,
                        labelColor = textColor
                    )
                )

                Spacer(modifier = Modifier.width(AgapSpacing.sm))

                val isResponder = uiState.selectedRole == "RESPONDER"
                FilterChip(
                    selected = isResponder,
                    onClick = { viewModel.onRoleSelected("RESPONDER") },
                    label = { Text("RESPONDER", style = MaterialTheme.typography.labelSmall) },
                    shape = AgapShape.sm,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = bgColor,
                        labelColor = textColor
                    )
                )

                Spacer(modifier = Modifier.width(AgapSpacing.sm))

                val isAdmin = uiState.selectedRole == "ADMIN"
                FilterChip(
                    selected = isAdmin,
                    onClick = { viewModel.onRoleSelected("ADMIN") },
                    label = { Text("ADMIN", style = MaterialTheme.typography.labelSmall) },
                    shape = AgapShape.sm,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = bgColor,
                        labelColor = textColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.md))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = subTextColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = bgColor.copy(alpha = 0.6f),
                    unfocusedContainerColor = bgColor.copy(alpha = 0.6f)
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = subTextColor) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = bgColor.copy(alpha = 0.6f),
                    unfocusedContainerColor = bgColor.copy(alpha = 0.6f)
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = { Text("Confirm Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = subTextColor) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = bgColor.copy(alpha = 0.6f),
                    unfocusedContainerColor = bgColor.copy(alpha = 0.6f)
                ),
                shape = AgapShape.sm
            )

            if (uiState.selectedRole == "RESPONDER") {
                Spacer(modifier = Modifier.height(AgapSpacing.sm))
                OutlinedTextField(
                    value = uiState.agencyId,
                    onValueChange = viewModel::onAgencyIdChanged,
                    label = { Text("Agency ID (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = subTextColor) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = borderCol,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedContainerColor = bgColor.copy(alpha = 0.6f),
                        unfocusedContainerColor = bgColor.copy(alpha = 0.6f)
                    ),
                    shape = AgapShape.sm
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text("Server API URL / IP") },
                leadingIcon = { Icon(Icons.Default.Engineering, contentDescription = null, tint = subTextColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = bgColor.copy(alpha = 0.6f),
                    unfocusedContainerColor = bgColor.copy(alpha = 0.6f)
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            Button(
                onClick = viewModel::register,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = AgapShape.sm,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "REGISTER",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            TextButton(onClick = onNavigateToLogin) {
                Text(
                    text = "Already have an account? Sign In",
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor
                )
            }
        }

        uiState.errorMessage?.let { error ->
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearErrorMessage) {
                        Text("DISMISS", color = Color.White)
                    }
                },
                containerColor = RedPrimary,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(error, color = Color.White)
            }
        }
    }
}

