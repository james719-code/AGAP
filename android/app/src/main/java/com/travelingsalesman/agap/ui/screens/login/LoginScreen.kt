package com.travelingsalesman.agap.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loginSuccessRole) {
        uiState.loginSuccessRole?.let { role ->
            onLoginSuccess(role)
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
            // Header Logo & Branding
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "AGAP",
                tint = RedPrimary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AGAP DISPATCH",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = SlateTextPrimary,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "Emergency Mesh Telemetry Platform",
                fontSize = 13.sp,
                color = SlateTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Form Inputs
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = SlateTextSecondary)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = SlateSurface,
                    focusedLabelColor = RedPrimary,
                    unfocusedLabelColor = SlateTextSecondary,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SlateTextSecondary)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = SlateTextSecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = SlateSurface,
                    focusedLabelColor = RedPrimary,
                    unfocusedLabelColor = SlateTextSecondary,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = viewModel::login,
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
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "SIGN IN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation to Register
            TextButton(onClick = onNavigateToRegister) {
                Text(
                    text = "Don't have an account? Register here",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Error Snackbar
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
