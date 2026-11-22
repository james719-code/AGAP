package com.travelingsalesman.agap.ui.screens.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapAuthAction
import com.travelingsalesman.agap.ui.components.AgapAuthError
import com.travelingsalesman.agap.ui.components.AgapAuthField
import com.travelingsalesman.agap.ui.components.AgapAuthForm
import com.travelingsalesman.agap.ui.components.AgapAuthLayout
import com.travelingsalesman.agap.ui.components.AgapAuthPassword
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(uiState.loginSuccessRole) {
        uiState.loginSuccessRole?.let(onLoginSuccess)
    }

    AgapAuthLayout(
        title = "Sign in",
        description = "Access your AGAP account to send alerts or respond to incidents.",
        modifier = modifier
    ) {
        AgapAuthForm {
            AgapAuthField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = "Username",
                icon = Icons.Outlined.Person,
                enabled = !uiState.isLoading
            )
            AgapAuthPassword(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Password",
                enabled = !uiState.isLoading,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { if (!uiState.isLoading) viewModel.login() })
            )
            uiState.errorMessage?.let { AgapAuthError(it) }
            AgapAuthAction(
                label = "Sign in",
                loadingLabel = "Signing in…",
                loading = uiState.isLoading,
                onClick = viewModel::login
            )
            HorizontalDivider(color = colors.outlineVariant)
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New to AGAP? Create an account", textAlign = TextAlign.Center)
            }
        }

        if (onContinueAsGuest != null) {
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                OutlinedButton(
                    onClick = onContinueAsGuest,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = AgapShape.pill,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Text("Continue as guest")
                }
                Text(
                    text = "Explore AGAP. Sign in when you need to send an alert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (viewModel.localTestAccounts.isNotEmpty()) {
            AgapAuthForm {
                Text("Offline test accounts", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Local data only. Password: ${viewModel.localTestAccounts.first().password}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    viewModel.localTestAccounts.forEach { account ->
                        FilledTonalButton(
                            onClick = { viewModel.loginAsTestAccount(account) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(AgapSpacing.sm)
                        ) {
                            Text(
                                account.role.lowercase().replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
