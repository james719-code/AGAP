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
import com.travelingsalesman.agap.ui.theme.GlassStrokeDark
import com.travelingsalesman.agap.ui.theme.GlassSurfaceDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary
import com.travelingsalesman.agap.ui.theme.SlateTextSecondary

@Composable
fun RegisterScreen(
    onRegisterSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.registerSuccessRole) {
        uiState.registerSuccessRole?.let { role ->
            onRegisterSuccess(role)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassSurfaceDark)
                .border(width = 1.dp, color = GlassStrokeDark, shape = RoundedCornerShape(22.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "AGAP",
                tint = RedPrimary,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "CREATE AGAP ACCOUNT",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = SlateTextPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = "Register for Citizen SOS or Responder Access",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Role selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val isCitizen = uiState.selectedRole == "CITIZEN"
                FilterChip(
                    selected = isCitizen,
                    onClick = { viewModel.onRoleSelected("CITIZEN") },
                    label = { Text("CITIZEN", fontWeight = if (isCitizen) FontWeight.Black else FontWeight.Medium) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = SlateBackground,
                        labelColor = SlateTextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                val isResponder = uiState.selectedRole == "RESPONDER"
                FilterChip(
                    selected = isResponder,
                    onClick = { viewModel.onRoleSelected("RESPONDER") },
                    label = { Text("RESPONDER", fontWeight = if (isResponder) FontWeight.Black else FontWeight.Medium) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = SlateBackground,
                        labelColor = SlateTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SlateTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = GlassStrokeDark,
                    focusedContainerColor = SlateBackground.copy(alpha = 0.6f),
                    unfocusedContainerColor = SlateBackground.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SlateTextSecondary) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = GlassStrokeDark,
                    focusedContainerColor = SlateBackground.copy(alpha = 0.6f),
                    unfocusedContainerColor = SlateBackground.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = { Text("Confirm Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SlateTextSecondary) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = GlassStrokeDark,
                    focusedContainerColor = SlateBackground.copy(alpha = 0.6f),
                    unfocusedContainerColor = SlateBackground.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (uiState.selectedRole == "RESPONDER") {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.agencyId,
                    onValueChange = viewModel::onAgencyIdChanged,
                    label = { Text("Agency ID (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = SlateTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = GlassStrokeDark,
                        focusedContainerColor = SlateBackground.copy(alpha = 0.6f),
                        unfocusedContainerColor = SlateBackground.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = viewModel::register,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text(
                    text = "Already have an account? Sign In",
                    color = SlateTextSecondary,
                    fontSize = 12.sp
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

