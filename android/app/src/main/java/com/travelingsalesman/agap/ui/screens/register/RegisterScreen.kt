package com.travelingsalesman.agap.ui.screens.register

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateSurface
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
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "AGAP",
                tint = RedPrimary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CREATE AGAP ACCOUNT",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = SlateTextPrimary
            )

            Text(
                text = "Register for Citizen SOS or Responder Access",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Role selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = uiState.selectedRole == "CITIZEN",
                    onClick = { viewModel.onRoleSelected("CITIZEN") },
                    label = { Text("CITIZEN") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(12.dp))

                FilterChip(
                    selected = uiState.selectedRole == "RESPONDER",
                    onClick = { viewModel.onRoleSelected("RESPONDER") },
                    label = { Text("RESPONDER") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RedPrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = SlateSurface,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = SlateSurface,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = { Text("Confirm Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = SlateSurface,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (uiState.selectedRole == "RESPONDER") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.agencyId,
                    onValueChange = viewModel::onAgencyIdChanged,
                    label = { Text("Agency ID (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = SlateSurface,
                        focusedContainerColor = SlateSurface,
                        unfocusedContainerColor = SlateSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::register,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text(
                    text = "Already have an account? Sign In",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
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
