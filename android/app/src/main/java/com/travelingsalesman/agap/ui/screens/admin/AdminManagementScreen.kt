package com.travelingsalesman.agap.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.normalizeResponderStatusValue
import com.travelingsalesman.agap.ui.components.AgapTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: (() -> Unit)? = null,
    showBackButton: Boolean = false,
    viewModel: AdminManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
    var selectedUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAgencyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedResponderId by rememberSaveable { mutableStateOf<Long?>(null) }

    val selectedUser = selectedUserId?.let { id ->
        uiState.selectedUserDetails?.takeIf { it.id == id }
            ?: uiState.usersList.firstOrNull { it.id == id }
    }
    val selectedAgency = selectedAgencyId?.let { id ->
        uiState.selectedAgencyDetails?.takeIf { it.id == id }
            ?: uiState.agenciesList.firstOrNull { it.id == id }
    }
    val selectedResponder = selectedResponderId?.let { id ->
        uiState.selectedResponderDetails?.takeIf { it.id == id }
            ?: uiState.responderUnitsList.firstOrNull { it.id == id }
    }

    var showAgencyDialog by remember { mutableStateOf(false) }
    var editingAgency by remember { mutableStateOf<AgencyDto?>(null) }
    var agencyCode by remember { mutableStateOf("") }
    var agencyName by remember { mutableStateOf("") }
    var agencyType by remember { mutableStateOf("DRRMO") }
    var agencyContact by remember { mutableStateOf("") }

    var showUnitDialog by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf<ResponderUnitDto?>(null) }
    var unitCallsign by remember { mutableStateOf("") }
    var unitAgencyId by remember { mutableStateOf(0L) }
    var unitTargetId by remember { mutableStateOf("") }
    var unitStatus by remember { mutableStateOf("IDLE") }

    LaunchedEffect(uiState.selectedTab) {
        searchQuery = ""
        selectedUserId = null
        selectedAgencyId = null
        selectedResponderId = null
    }

    LaunchedEffect(selectedUserId) {
        selectedUserId?.let(viewModel::loadUserDetails)
    }

    LaunchedEffect(selectedAgencyId) {
        selectedAgencyId?.let(viewModel::loadAgencyDetails)
    }

    LaunchedEffect(selectedResponderId) {
        selectedResponderId?.let(viewModel::loadResponderDetails)
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedbackMessage()
    }

    val openAgencyEditor = {
        editingAgency = null
        agencyCode = ""
        agencyName = ""
        agencyType = "DRRMO"
        agencyContact = ""
        showAgencyDialog = true
    }

    val handleBack = {
        when {
            selectedUser != null -> selectedUserId = null
            selectedAgency != null -> selectedAgencyId = null
            selectedResponder != null -> selectedResponderId = null
            else -> onNavigateBack()
        }
    }

    BackHandler(
        enabled = (selectedUser != null || selectedAgency != null || selectedResponder != null) &&
            !showAgencyDialog &&
            !showUnitDialog &&
            confirmation == null,
        onBack = handleBack
    )

    Scaffold(
        topBar = {
            AgapTopBar(
                title = when {
                    selectedUser != null -> "ACCOUNT DETAILS"
                    selectedAgency != null -> "AGENCY DETAILS"
                    selectedResponder != null -> "UNIT DETAILS"
                    else -> "SYSTEM MANAGEMENT"
                },
                showBackButton = selectedUser != null || selectedAgency != null || selectedResponder != null || showBackButton,
                onBackClick = handleBack,
                onNotificationClick = onNavigateToNotifications,
                onMessageClick = onNavigateToMessages
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedUser == null && selectedAgency == null && uiState.selectedTab == 1) {
                FloatingActionButton(
                    onClick = openAgencyEditor,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add agency")
                }
            } else if (selectedUser == null && selectedResponder == null && uiState.selectedTab == 2) {
                FloatingActionButton(
                    onClick = {
                        editingUnit = null
                        unitCallsign = ""
                        unitAgencyId = uiState.agenciesList.firstOrNull()?.id ?: 0L
                        unitTargetId = ""
                        unitStatus = "IDLE"
                        showUnitDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add responder unit")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refreshAllData,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectedUser != null && uiState.selectedTab == 0) {
                    AdminUserDetailsContent(
                        user = selectedUser,
                        onApprove = { viewModel.verifyUserAccount(selectedUser.id) },
                        onReject = {
                            confirmation = ConfirmationRequest(
                                title = "Reject registration?",
                                message = "${selectedUser.displayName()} will not be able to use this account unless an administrator changes the status later.",
                                confirmLabel = "REJECT"
                            ) { viewModel.rejectUserAccount(selectedUser.id) }
                        },
                        onSuspend = {
                            confirmation = ConfirmationRequest(
                                title = "Suspend account?",
                                message = "${selectedUser.displayName()} will temporarily lose access to their AGAP account until an administrator restores it.",
                                confirmLabel = "SUSPEND"
                            ) { viewModel.suspendUserAccount(selectedUser.id) }
                        },
                        onRestore = {
                            confirmation = ConfirmationRequest(
                                title = "Restore account access?",
                                message = "${selectedUser.displayName()} will regain access to their AGAP account and features.",
                                confirmLabel = "RESTORE",
                                destructive = false
                            ) { viewModel.verifyUserAccount(selectedUser.id) }
                        }
                    )
                } else if (selectedAgency != null && uiState.selectedTab == 1) {
                    AgencyDetailsContent(
                        agency = selectedAgency,
                        units = uiState.responderUnitsList,
                        onEdit = {
                            editingAgency = selectedAgency
                            agencyCode = selectedAgency.code
                            agencyName = selectedAgency.name
                            agencyType = selectedAgency.type
                            agencyContact = selectedAgency.contactNumber.orEmpty()
                            showAgencyDialog = true
                        },
                        onDelete = {
                            confirmation = ConfirmationRequest(
                                title = "Delete agency?",
                                message = "Delete ${selectedAgency.name}? Units attached to this agency should be reassigned first.",
                                confirmLabel = "DELETE"
                            ) {
                                viewModel.deleteAgency(selectedAgency.id)
                                selectedAgencyId = null
                            }
                        }
                    )
                } else if (selectedResponder != null && uiState.selectedTab == 2) {
                    ResponderUnitDetailsContent(
                        unit = selectedResponder,
                        agency = uiState.agenciesList.firstOrNull { it.id == selectedResponder.agencyId },
                        onEdit = {
                            editingUnit = selectedResponder
                            unitCallsign = selectedResponder.unitCallsign
                            unitAgencyId = selectedResponder.agencyId
                            unitTargetId = selectedResponder.targetId
                            unitStatus = normalizeResponderStatusValue(selectedResponder.status)
                            showUnitDialog = true
                        },
                        onDelete = {
                            confirmation = ConfirmationRequest(
                                title = "Delete responder unit?",
                                message = "Delete unit ${selectedResponder.unitCallsign}? Active incident dispatches should be completed first.",
                                confirmLabel = "DELETE"
                            ) {
                                viewModel.deleteResponderUnit(selectedResponder.id)
                                selectedResponderId = null
                            }
                        }
                    )
                } else {
                    ManagementTabs(
                        selectedTab = uiState.selectedTab,
                        onSelected = viewModel::selectTab
                    )

                    when (uiState.selectedTab) {
                        0 -> UserManagementContent(
                            users = uiState.usersList,
                            currentUserId = uiState.currentUserId,
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { searchQuery = it },
                            onUserClick = { user -> selectedUserId = user.id }
                        )

                        1 -> AgencyManagementContent(
                            agencies = uiState.agenciesList,
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { searchQuery = it },
                            onAgencyClick = { agency -> selectedAgencyId = agency.id },
                            onEdit = { agency ->
                                editingAgency = agency
                                agencyCode = agency.code
                                agencyName = agency.name
                                agencyType = agency.type
                                agencyContact = agency.contactNumber.orEmpty()
                                showAgencyDialog = true
                            },
                            onDelete = { agency ->
                                confirmation = ConfirmationRequest(
                                    title = "Delete agency?",
                                    message = "Delete ${agency.name}? Units attached to this agency should be reassigned first.",
                                    confirmLabel = "DELETE"
                                ) { viewModel.deleteAgency(agency.id) }
                            }
                        )

                        2 -> ResponderManagementContent(
                            units = uiState.responderUnitsList,
                            agencies = uiState.agenciesList,
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { searchQuery = it },
                            onUnitClick = { unit -> selectedResponderId = unit.id },
                            onAdd = {
                                editingUnit = null
                                unitCallsign = ""
                                unitAgencyId = uiState.agenciesList.firstOrNull()?.id ?: 0L
                                unitTargetId = ""
                                unitStatus = "IDLE"
                                showUnitDialog = true
                            },
                            onEdit = { unit ->
                                editingUnit = unit
                                unitCallsign = unit.unitCallsign
                                unitAgencyId = unit.agencyId
                                unitTargetId = unit.targetId
                                unitStatus = normalizeResponderStatusValue(unit.status)
                                showUnitDialog = true
                            },
                            onDelete = { unit ->
                                confirmation = ConfirmationRequest(
                                    title = "Delete responder unit?",
                                    message = "Delete unit ${unit.unitCallsign}? Active incident dispatches should be completed first.",
                                    confirmLabel = "DELETE"
                                ) { viewModel.deleteResponderUnit(unit.id) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAgencyDialog) {
        AgencyEditorDialog(
            isEditing = editingAgency != null,
            agencyName = agencyName,
            agencyCode = agencyCode,
            agencyType = agencyType,
            agencyContact = agencyContact,
            onNameChange = { agencyName = it },
            onCodeChange = { agencyCode = it },
            onTypeChange = { agencyType = it },
            onContactChange = { agencyContact = it },
            onDismiss = { showAgencyDialog = false },
            onSave = {
                val current = editingAgency
                if (current == null) {
                    viewModel.createAgency(
                        code = agencyCode.trim(),
                        name = agencyName.trim(),
                        type = agencyType,
                        contact = agencyContact.trim()
                    )
                } else {
                    viewModel.updateAgency(
                        id = current.id,
                        code = agencyCode.trim(),
                        name = agencyName.trim(),
                        type = agencyType,
                        contact = agencyContact.trim()
                    )
                }
                showAgencyDialog = false
            }
        )
    }

    if (showUnitDialog) {
        ResponderEditorDialog(
            isEditing = editingUnit != null,
            callsign = unitCallsign,
            agencyId = unitAgencyId,
            targetId = unitTargetId,
            status = unitStatus,
            agencies = uiState.agenciesList,
            onCallsignChange = { unitCallsign = it },
            onAgencyChange = { unitAgencyId = it },
            onTargetIdChange = { unitTargetId = it },
            onStatusChange = { unitStatus = it },
            onDismiss = { showUnitDialog = false },
            onSave = {
                val current = editingUnit
                if (current == null) {
                    viewModel.createResponderUnit(
                        agencyId = unitAgencyId,
                        unitCallsign = unitCallsign.trim(),
                        responderName = unitCallsign.trim(),
                        phone = "",
                        targetId = unitTargetId.trim()
                    )
                } else {
                    viewModel.updateResponderUnit(
                        id = current.id,
                        agencyId = unitAgencyId,
                        unitCallsign = unitCallsign.trim(),
                        status = unitStatus,
                        targetId = unitTargetId.trim()
                    )
                }
                showUnitDialog = false
            }
        )
    }

    confirmation?.let { req ->
        ConfirmationDialog(
            request = req,
            onDismiss = { confirmation = null },
            onConfirm = {
                req.onConfirm()
                confirmation = null
            }
        )
    }
}
