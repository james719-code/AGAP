package com.travelingsalesman.agap.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapBrand
import com.travelingsalesman.agap.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(uiState.loginSuccessRole) {
        uiState.loginSuccessRole?.let(onLoginSuccess)
    }
    Box(modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().align(Alignment.Center)
                .verticalScroll(rememberScrollState()).padding(AgapSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                AgapBrand(subtitle = "Assistance & protection")
                Surface(shape = AgapShape.circle, color = colors.secondaryContainer) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.HealthAndSafety, null, tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(27.dp))
                    }
                }
            }
            Spacer(Modifier.height(AgapSpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                Text("Closer to help.\nWherever you are.", style = MaterialTheme.typography.displayMedium,
                    color = colors.onBackground)
                Text("Connected by community. Ready when it matters.", style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(AgapSpacing.xs))
            Surface(shape = AgapShape.xxl, color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.padding(AgapSpacing.xl), verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
                    Text("Welcome back", style = MaterialTheme.typography.titleLarge)
                    Text("Sign in to send alerts and reach responders.", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                    OutlinedTextField(
                        value = uiState.username, onValueChange = viewModel::onUsernameChanged,
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true, shape = AgapShape.md, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.outlineVariant)
                    )
                    OutlinedTextField(
                        value = uiState.password, onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (passwordVisible) "Hide password" else "Show password")
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!uiState.isLoading) viewModel.login() }),
                        singleLine = true, shape = AgapShape.md, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.outlineVariant)
                    )
                    Button(onClick = viewModel::login, enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = AgapShape.lg) {
                        if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                        else {
                            Text("Sign in", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
                        }
                    }
                    TextButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
                        Text("New to AGAP? Create an account", textAlign = TextAlign.Center)
                    }
                }
            }
            if (onContinueAsGuest != null) {
                OutlinedButton(onClick = onContinueAsGuest, enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = AgapShape.lg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
                    Text("Continue as guest")
                }
                Text("Take a look around. Sign in when you need to send an alert.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (viewModel.localTestAccounts.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                Text("Offline test accounts", style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
                Text("Local data only • Password: ${viewModel.localTestAccounts.first().password}",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    viewModel.localTestAccounts.forEach { account ->
                        FilledTonalButton(onClick = { viewModel.loginAsTestAccount(account) },
                            enabled = !uiState.isLoading, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(AgapSpacing.sm)) {
                            Text(account.role.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        uiState.errorMessage?.let { error ->
            Snackbar(action = { TextButton(onClick = viewModel::clearErrorMessage) { Text("Dismiss") } },
                containerColor = colors.errorContainer, contentColor = colors.onErrorContainer,
                modifier = Modifier.align(Alignment.BottomCenter).padding(AgapSpacing.lg)) { Text(error) }
        }
    }
}
