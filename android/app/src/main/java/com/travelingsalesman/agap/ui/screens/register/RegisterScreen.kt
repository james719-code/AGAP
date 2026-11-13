package com.travelingsalesman.agap.ui.screens.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapAuthAction
import com.travelingsalesman.agap.ui.components.AgapAuthError
import com.travelingsalesman.agap.ui.components.AgapAuthField
import com.travelingsalesman.agap.ui.components.AgapAuthForm
import com.travelingsalesman.agap.ui.components.AgapAuthLayout
import com.travelingsalesman.agap.ui.components.AgapAuthPassword
import com.travelingsalesman.agap.ui.components.agapAuthFieldColors
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun RegisterScreen(
    onRegisterSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val enabled = !uiState.isLoading
    val isResponder = uiState.selectedRole == "RESPONDER"

    LaunchedEffect(uiState.registerSuccessRole) {
        uiState.registerSuccessRole?.let(onRegisterSuccess)
    }

    AgapAuthLayout(
        title = "Create an account",
        description = "Join AGAP as a citizen or emergency responder.",
        modifier = modifier
    ) {
        AgapAuthForm {
            Text("Account type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("CITIZEN" to "Citizen", "RESPONDER" to "Responder").forEachIndexed { index, (role, label) ->
                    SegmentedButton(
                        selected = uiState.selectedRole == role,
                        onClick = { viewModel.onRoleSelected(role) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.primaryContainer,
                            activeContentColor = colors.onPrimaryContainer,
                            activeBorderColor = colors.primary,
                            inactiveBorderColor = colors.outline
                        ),
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = if (isResponder) "Join your agency’s rescue unit to receive assignments."
                    else "Send alerts and keep track of your incident reports.",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            if (isResponder) {
                ResponderAffiliation(uiState, viewModel)
            }

            HorizontalDivider(color = colors.outlineVariant)
            Text("Sign-in details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            AgapAuthField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = "Username",
                icon = Icons.Outlined.Person,
                enabled = enabled
            )
            AgapAuthPassword(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Password",
                enabled = enabled
            )
            AgapAuthPassword(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = "Confirm password",
                visibilityLabel = "password confirmation",
                enabled = enabled
            )

            HorizontalDivider(color = colors.outlineVariant)
            Text("Contact details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            AgapAuthField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                label = "Full name (optional)",
                icon = Icons.Outlined.Person,
                enabled = enabled
            )
            AgapAuthField(
                value = uiState.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChanged,
                label = "Phone number (optional)",
                icon = Icons.Outlined.Phone,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
            )

            uiState.errorMessage?.let { AgapAuthError(it) }
            AgapAuthAction(
                label = "Create account",
                loadingLabel = "Creating account…",
                loading = uiState.isLoading,
                onClick = viewModel::register
            )
            HorizontalDivider(color = colors.outlineVariant)
            TextButton(onClick = onNavigateToLogin, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Already registered? Sign in")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponderAffiliation(uiState: RegisterUiState, viewModel: RegisterViewModel) {
    val enabled = !uiState.isLoading
    val colors = MaterialTheme.colorScheme
    var agencyExpanded by remember { mutableStateOf(false) }
    var teamExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = agencyExpanded,
        onExpandedChange = { agencyExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = uiState.selectedAgency?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Assigned agency") },
            placeholder = { Text("Select an agency") },
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agencyExpanded) },
            colors = agapAuthFieldColors(),
            shape = AgapShape.md,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = agencyExpanded, onDismissRequest = { agencyExpanded = false }) {
            uiState.availableAgencies.forEach { agency ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                            Text(agency.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${agency.code} · ${agency.type}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        viewModel.onAgencySelected(agency)
                        agencyExpanded = false
                    }
                )
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = teamExpanded,
        onExpandedChange = { teamExpanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = uiState.selectedTeam?.let { "${it.unitCallsign} (${it.status})" }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Rescue unit") },
            placeholder = { Text("Select a rescue unit") },
            leadingIcon = { Icon(Icons.Outlined.Engineering, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamExpanded) },
            colors = agapAuthFieldColors(),
            shape = AgapShape.md,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = teamExpanded, onDismissRequest = { teamExpanded = false }) {
            if (uiState.availableTeams.isEmpty()) {
                Text(
                    text = "No active rescue units available for this agency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(AgapSpacing.lg)
                )
            }
            uiState.availableTeams.forEach { team ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                            Text(team.unitCallsign, style = MaterialTheme.typography.bodyMedium)
                            Text("${team.status} · ${team.targetId.ifEmpty { "General" }}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        viewModel.onTeamSelected(team)
                        teamExpanded = false
                    }
                )
            }
        }
    }
}
