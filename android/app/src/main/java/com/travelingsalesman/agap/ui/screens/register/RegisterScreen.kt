package com.travelingsalesman.agap.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapBrand
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inputBgColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)

    LaunchedEffect(uiState.registerSuccessRole) {
        uiState.registerSuccessRole?.let { role ->
            onRegisterSuccess(role)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()
            .imePadding()
            .padding(AgapSpacing.xl)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .clip(AgapShape.xxl)
                .background(cardBg)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = AgapShape.xxl)
                .verticalScroll(rememberScrollState())
                .padding(AgapSpacing.xxl),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            AgapBrand(subtitle = "Assistance & protection")

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            Text(
                text = "Create your account",
                style = MaterialTheme.typography.headlineMedium,
                color = textColor,
                textAlign = TextAlign.Start,
                letterSpacing = 0.sp
            )

            Text(
                text = "Stay connected when it matters most.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color = subTextColor
            )

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            // Role selection chips (Strictly CITIZEN and RESPONDER; ADMIN is backend-only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val isCitizen = uiState.selectedRole == "CITIZEN"
                FilterChip(
                    selected = isCitizen,
                    onClick = { viewModel.onRoleSelected("CITIZEN") },
                    label = { Text("Citizen", style = MaterialTheme.typography.labelLarge) },
                    shape = AgapShape.sm,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = inputBgColor,
                        labelColor = textColor
                    )
                )

                Spacer(modifier = Modifier.width(AgapSpacing.sm))

                val isResponder = uiState.selectedRole == "RESPONDER"
                FilterChip(
                    selected = isResponder,
                    onClick = { viewModel.onRoleSelected("RESPONDER") },
                    label = { Text("Responder", style = MaterialTheme.typography.labelLarge) },
                    shape = AgapShape.sm,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = inputBgColor,
                        labelColor = textColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.md))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("Username *") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = subTextColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Full Name (Optional)") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = subTextColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChanged,
                label = { Text("Phone Number (Optional)") },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, tint = subTextColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password *") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = subTextColor) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
                ),
                shape = AgapShape.sm
            )

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                label = { Text("Confirm Password *") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = subTextColor) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = inputBgColor,
                    unfocusedContainerColor = inputBgColor
                ),
                shape = AgapShape.sm
            )

            if (uiState.selectedRole == "RESPONDER") {
                Spacer(modifier = Modifier.height(AgapSpacing.sm))
                var agencyExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = agencyExpanded,
                    onExpandedChange = { agencyExpanded = !agencyExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = uiState.selectedAgency?.name ?: "Select Agency",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned Agency *") },
                        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = subTextColor) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agencyExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor
                        ),
                        shape = AgapShape.sm,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = agencyExpanded,
                        onDismissRequest = { agencyExpanded = false },
                        modifier = Modifier.background(cardBg)
                    ) {
                        uiState.availableAgencies.forEach { agency ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(text = agency.name, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Code: ${agency.code} • Type: ${agency.type}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
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

                Spacer(modifier = Modifier.height(AgapSpacing.sm))
                var teamExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = teamExpanded,
                    onExpandedChange = { teamExpanded = !teamExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedTeamText = uiState.selectedTeam?.let { "${it.unitCallsign} (${it.status})" } ?: if (uiState.availableTeams.isEmpty()) "No Teams Available for Agency" else "Select Responder Team"
                    OutlinedTextField(
                        value = selectedTeamText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Responder Team (Rescue Unit) *") },
                        leadingIcon = { Icon(Icons.Outlined.Engineering, contentDescription = null, tint = subTextColor) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor
                        ),
                        shape = AgapShape.sm,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = teamExpanded,
                        onDismissRequest = { teamExpanded = false },
                        modifier = Modifier.background(cardBg)
                    ) {
                        if (uiState.availableTeams.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(text = "No active teams found for this office", color = subTextColor, style = MaterialTheme.typography.bodySmall) },
                                onClick = { teamExpanded = false }
                            )
                        } else {
                            uiState.availableTeams.forEach { team ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = team.unitCallsign, color = textColor, style = MaterialTheme.typography.bodyMedium)
                                            Text(text = "Status: ${team.status} • Target: ${team.targetId.ifEmpty { "General" }}", color = subTextColor, style = MaterialTheme.typography.bodySmall)
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
            }

            Spacer(modifier = Modifier.height(AgapSpacing.xl))

            Button(
                onClick = viewModel::register,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = AgapShape.lg,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Create account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = 0.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))

            TextButton(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Already registered? Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = primaryColor
                )
            }
        }

        if (uiState.errorMessage != null) {
            val error = uiState.errorMessage!!
            Snackbar(
                action = {
                    TextButton(onClick = viewModel::clearErrorMessage) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
