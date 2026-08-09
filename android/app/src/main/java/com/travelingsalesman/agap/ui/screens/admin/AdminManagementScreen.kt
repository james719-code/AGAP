package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UserDto
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.PaletteOrangeDark
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.util.Locale

private const val MANAGEMENT_BATCH_SIZE = 15
private const val MANAGEMENT_LARGE_BATCH_SIZE = 30

private val agencyTypes = listOf("DRRMO", "RESCUE", "POLICE", "FIRE", "MEDICAL", "VOLUNTEER")
private val responderStatuses = listOf("IDLE", "DISPATCHED", "OFFLINE")

private data class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean = true,
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(
    onNavigateBack: () -> Unit = {},
    showBackButton: Boolean = false,
    viewModel: AdminManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }
    var selectedUserId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedUser = selectedUserId?.let { id ->
        uiState.selectedUserDetails?.takeIf { it.id == id }
            ?: uiState.usersList.firstOrNull { it.id == id }
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
    }

    LaunchedEffect(selectedUserId) {
        selectedUserId?.let(viewModel::loadUserDetails)
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedbackMessage()
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = if (selectedUser != null) "ACCOUNT DETAILS" else "SYSTEM MANAGEMENT",
                showBackButton = selectedUser != null || showBackButton,
                onBackClick = {
                    if (selectedUser != null) selectedUserId = null else onNavigateBack()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                                message = "${selectedUser.displayName()} will lose access to AGAP until the account is restored.",
                                confirmLabel = "SUSPEND"
                            ) { viewModel.suspendUserAccount(selectedUser.id) }
                        },
                        onRestore = { viewModel.verifyUserAccount(selectedUser.id) }
                    )
                } else {
                    ManagementTabs(
                        selectedTab = uiState.selectedTab,
                        onSelected = viewModel::selectTab
                    )

                    when (uiState.selectedTab) {
                    0 -> UserManagementContent(
                        users = uiState.usersList,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        onUserClick = { selectedUserId = it.id }
                    )

                    1 -> AgencyManagementContent(
                        agencies = uiState.agenciesList,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        onAdd = {
                            editingAgency = null
                            agencyCode = ""
                            agencyName = ""
                            agencyType = "DRRMO"
                            agencyContact = ""
                            showAgencyDialog = true
                        },
                        onEdit = { agency ->
                            editingAgency = agency
                            agencyCode = agency.code
                            agencyName = agency.name
                            agencyType = agency.type.takeIf { it in agencyTypes } ?: "DRRMO"
                            agencyContact = agency.contactNumber
                            showAgencyDialog = true
                        },
                        onDelete = { agency ->
                            confirmation = ConfirmationRequest(
                                title = "Delete agency?",
                                message = "${agency.name} will be removed from the dispatch directory. Make sure no responder units still depend on it.",
                                confirmLabel = "DELETE"
                            ) { viewModel.deleteAgency(agency.id) }
                        }
                    )

                    2 -> ResponderManagementContent(
                        units = uiState.responderUnitsList,
                        agencies = uiState.agenciesList,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
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
                            unitStatus = unit.status.takeIf { it in responderStatuses } ?: "IDLE"
                            showUnitDialog = true
                        },
                        onDelete = { unit ->
                            confirmation = ConfirmationRequest(
                                title = "Delete responder unit?",
                                message = "${unit.unitCallsign} will be removed from dispatch availability and unit management.",
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
            onCodeChange = { agencyCode = it.uppercase(Locale.getDefault()) },
            onTypeChange = { agencyType = it },
            onContactChange = { agencyContact = it },
            onDismiss = { showAgencyDialog = false },
            onSave = {
                val target = editingAgency
                if (target == null) {
                    viewModel.createAgency(
                        agencyCode.trim().uppercase(Locale.getDefault()),
                        agencyName.trim(),
                        agencyType,
                        agencyContact.trim()
                    )
                } else {
                    viewModel.updateAgency(
                        target.id,
                        agencyCode.trim().uppercase(Locale.getDefault()),
                        agencyName.trim(),
                        agencyType,
                        agencyContact.trim()
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
            onCallsignChange = { unitCallsign = it.uppercase(Locale.getDefault()) },
            onAgencyChange = { unitAgencyId = it },
            onTargetIdChange = { unitTargetId = it },
            onStatusChange = { unitStatus = it },
            onDismiss = { showUnitDialog = false },
            onSave = {
                val target = editingUnit
                if (target == null) {
                    viewModel.createResponderUnit(
                        agencyId = unitAgencyId,
                        unitCallsign = unitCallsign.trim(),
                        responderName = "",
                        phone = "",
                        targetId = unitTargetId.trim()
                    )
                } else {
                    viewModel.updateResponderUnit(
                        id = target.id,
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

    confirmation?.let { request ->
        ConfirmationDialog(
            request = request,
            onDismiss = { confirmation = null },
            onConfirm = {
                request.onConfirm()
                confirmation = null
            }
        )
    }
}

@Composable
private fun ManagementTabs(
    selectedTab: Int,
    onSelected: (Int) -> Unit
) {
    val labels = listOf("USERS", "AGENCIES", "UNITS")

    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = RedPrimary,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelected(index) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        color = if (selectedTab == index) RedPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Composable
private fun UserManagementContent(
    users: List<UserDto>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onUserClick: (UserDto) -> Unit
) {
    val pending =
        remember(users) {
            users.filter {
                it.accountStatus.equals(
                    "PENDING",
                    true
                )
            }
        }

    val approved =
        remember(users) {
            users.filter {
                it.accountStatus.equals(
                    "APPROVED",
                    true
                )
            }
        }

    val restricted =
        remember(users) {
            users.filter {
                it.accountStatus.equals(
                    "REJECTED",
                    true
                ) ||
                        it.accountStatus.equals(
                            "SUSPENDED",
                            true
                        )
            }
        }

    val query =
        searchQuery.trim()

    val filteredPending =
        remember(pending, query) {
            pending
                .filter {
                    it.matches(query)
                }
                .sortedBy {
                    it.displayName()
                        .lowercase(
                            Locale.getDefault()
                        )
                }
        }

    val filteredApproved =
        remember(approved, query) {
            approved
                .filter {
                    it.matches(query)
                }
                .sortedBy {
                    it.displayName()
                        .lowercase(
                            Locale.getDefault()
                        )
                }
        }

    val filteredRestricted =
        remember(restricted, query) {
            restricted
                .filter {
                    it.matches(query)
                }
                .sortedBy {
                    it.displayName()
                        .lowercase(
                            Locale.getDefault()
                        )
                }
        }

    var pendingLimit by
    rememberSaveable(query) {
        mutableIntStateOf(
            MANAGEMENT_BATCH_SIZE
        )
    }

    var approvedLimit by
    rememberSaveable(query) {
        mutableIntStateOf(
            MANAGEMENT_BATCH_SIZE
        )
    }

    var restrictedLimit by
    rememberSaveable(query) {
        mutableIntStateOf(
            MANAGEMENT_BATCH_SIZE
        )
    }

    val visiblePending =
        filteredPending.take(
            pendingLimit
        )

    val visibleApproved =
        filteredApproved.take(
            approvedLimit
        )

    val visibleRestricted =
        filteredRestricted.take(
            restrictedLimit
        )

    val visibleCount =
        filteredPending.size +
                filteredApproved.size +
                filteredRestricted.size

    var selectedStatusFilter by rememberSaveable { mutableStateOf("ALL") }

    val selectedFilteredCount = when (selectedStatusFilter) {
        "PENDING" -> filteredPending.size
        "APPROVED" -> filteredApproved.size
        "RESTRICTED" -> filteredRestricted.size
        else -> visibleCount
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            ManagementSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = "Search name, ID, role, phone or email"
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to users.size,
                    "PENDING" to pending.size,
                    "APPROVED" to approved.size,
                    "RESTRICTED" to restricted.size
                ).forEach { (filterKey, count) ->
                    FilterChip(
                        selected = selectedStatusFilter == filterKey,
                        onClick = { selectedStatusFilter = filterKey },
                        label = {
                            Text(
                                text = "$filterKey ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedStatusFilter == filterKey) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = RedPrimary
                        )
                    )
                }
            }
        }

        if (selectedFilteredCount == 0) {
            item {
                EmptyManagementState(
                    title = if (query.isBlank()) "No users in this filter" else "No matching users",
                    message = if (query.isBlank()) {
                        "Try another status filter to view more accounts."
                    } else {
                        "Try a name, account ID, role, phone number or email address."
                    }
                )
            }
        } else {
            if (selectedStatusFilter == "ALL" || selectedStatusFilter == "PENDING") {
                items(
                    items =
                        visiblePending,
                    key = {
                        "pending-${it.id}"
                    },
                    contentType = {
                        "pending_user"
                    }
                ) { user ->
                    UserAccountCard(
                        user = user,
                        onClick = { onUserClick(user) }
                    )
                }

                if (visiblePending.size < filteredPending.size) {
                    item {
                        ManagementLoadMore(
                            showing = visiblePending.size,
                            total = filteredPending.size,
                            onLoadMore = { pendingLimit += MANAGEMENT_BATCH_SIZE }
                        )
                    }
                }
            }

            if (selectedStatusFilter == "ALL" || selectedStatusFilter == "APPROVED") {
                items(
                    items =
                        visibleApproved,
                    key = {
                        "approved-${it.id}"
                    },
                    contentType = {
                        "approved_user"
                    }
                ) { user ->
                    UserAccountCard(
                        user = user,
                        onClick = { onUserClick(user) }
                    )
                }

                if (visibleApproved.size < filteredApproved.size) {
                    item {
                        ManagementLoadMore(
                            showing = visibleApproved.size,
                            total = filteredApproved.size,
                            onLoadMore = { approvedLimit += MANAGEMENT_BATCH_SIZE }
                        )
                    }
                }
            }

            if (selectedStatusFilter == "ALL" || selectedStatusFilter == "RESTRICTED") {
                items(
                    items = visibleRestricted,
                    key = { "restricted-${it.id}" },
                    contentType = { "restricted_user" }
                ) { user ->
                    UserAccountCard(
                        user = user,
                        onClick = { onUserClick(user) }
                    )
                }

                if (visibleRestricted.size < filteredRestricted.size) {
                    item {
                        ManagementLoadMore(
                            showing = visibleRestricted.size,
                            total = filteredRestricted.size,
                            onLoadMore = { restrictedLimit += MANAGEMENT_BATCH_SIZE }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementLoadMore(
    showing: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    OutlinedButton(
        onClick = onLoadMore,
        modifier =
            Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme
                .surfaceVariant
        ),
        colors =
            ButtonDefaults
                .outlinedButtonColors(
                    contentColor =
                        MaterialTheme.colorScheme
                            .onSurface
                )
    ) {
        Text(
            text =
                "SHOW MORE  •  $showing / $total",
            fontWeight =
                FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.45.sp
        )
    }
}

@Composable
private fun UserAccountCard(
    user: UserDto,
    onClick: () -> Unit
) {
    val status = user.accountStatus.uppercase(Locale.ROOT)
    val statusColor = accountStatusColor(status)
    val roleIcon = if (user.role.equals("RESPONDER", true)) {
        Icons.Outlined.LocalHospital
    } else {
        Icons.Outlined.Person
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .glassCard(
                cornerShape = AgapShape.lg,
                borderColor = statusColor.copy(alpha = 0.24f)
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AgapShape.sm)
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.28f), AgapShape.sm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = roleIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${user.role.replace('_', ' ').uppercase(Locale.ROOT)}  |  ID ${user.displayId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))
            StatusPill(text = status, color = statusColor)
        }
    }
}

@Composable
private fun AdminUserDetailsContent(
    user: UserDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit,
    onRestore: () -> Unit
) {
    val status = user.accountStatus.uppercase(Locale.ROOT)
    val statusColor = accountStatusColor(status)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(
                        cornerShape = AgapShape.xl,
                        borderColor = statusColor.copy(alpha = 0.28f)
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(AgapSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(AgapShape.lg)
                            .background(statusColor.copy(alpha = 0.13f))
                            .border(1.dp, statusColor.copy(alpha = 0.32f), AgapShape.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (user.role.equals("RESPONDER", true)) {
                                Icons.Outlined.LocalHospital
                            } else {
                                Icons.Outlined.Person
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    Spacer(Modifier.width(AgapSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "${user.role.replace('_', ' ').uppercase(Locale.ROOT)}  |  ID ${user.displayId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(text = status, color = statusColor)
                }
            }
        }

        item {
            UserDetailSection("ACCOUNT") {
                UserDetailRow("Username", user.username)
                UserDetailRow("Account ID", user.id.toString())
                UserDetailRow("Role", user.role.replace('_', ' '))
                UserDetailRow("Verified", if (user.isVerified) "Yes" else "No")
                UserDetailRow("Registration device", user.deviceId.maskedValue())
                UserDetailRow("Agency ID", user.agencyId?.toString().orNotProvided())
                UserDetailRow("Address ID", user.addressId?.toString().orNotProvided())
            }
        }

        item {
            UserDetailSection("PROFILE & CONTACT") {
                UserDetailRow("Phone", user.phoneNumber.orNotProvided())
                UserDetailRow("Email", user.email.orNotProvided())
                UserDetailRow("Birthday", user.birthday.orNotProvided())
                UserDetailRow("Gender", user.gender.orNotProvided())
                UserDetailRow("National ID", user.nationalId?.maskedValue().orNotProvided())
                UserDetailRow("Medical information", user.medicalInfo.orNotProvided(), maxLines = 4)
                UserDetailRow("Image URL", user.imageUrl.orNotProvided(), maxLines = 2)
            }
        }

        item {
            UserDetailSection("ACTIVITY") {
                UserDetailRow("Last login", user.lastLoginAt.orNotProvided())
                UserDetailRow("Created", user.createdAt.orNotProvided())
                UserDetailRow("Last updated", user.updatedAt.orNotProvided())
            }
        }

        item {
            UserDetailSection("REGISTERED DEVICES") {
                if (user.devices.isEmpty()) {
                    UserDetailRow("Devices", "No registered devices")
                } else {
                    UserDetailRow("Devices", "${user.devices.size} registered")
                    user.devices.forEach { device ->
                        UserDetailItem(
                            title = device.deviceName.ifBlank { "Unnamed device" },
                            subtitle = "${device.platform.ifBlank { "Unknown platform" }}  |  ${device.deviceHardwareId.maskedValue()}",
                            detail = "ID ${device.id}  |  Last seen: ${device.lastSeenAt.orNotProvided()}  |  Added: ${device.createdAt.orNotProvided()}"
                        )
                    }
                }
            }
        }

        item {
            UserDetailSection("EMERGENCY CONTACTS") {
                if (user.emergencyContacts.isEmpty()) {
                    UserDetailRow("Contacts", "No emergency contacts")
                } else {
                    UserDetailRow("Contacts", "${user.emergencyContacts.size} saved")
                    user.emergencyContacts.forEach { contact ->
                        UserDetailItem(
                            title = contact.contactName,
                            subtitle = "${contact.relationship.ifBlank { "Emergency contact" }}  |  ${contact.phoneNumber}",
                            detail = "ID ${contact.id}  |  ${if (contact.isPrimary) "Primary" else "Secondary"}  |  Added: ${contact.createdAt.orNotProvided()}"
                        )
                    }
                }
            }
        }

        item {
            when (status) {
                "PENDING" -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                ) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenActive),
                        shape = AgapShape.md
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("APPROVE", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).height(46.dp),
                        border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                        shape = AgapShape.md
                    ) {
                        Text("REJECT", fontWeight = FontWeight.Bold)
                    }
                }

                "APPROVED" -> OutlinedButton(
                    onClick = onSuspend,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.60f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                    shape = AgapShape.md
                ) {
                    Text("SUSPEND ACCOUNT", fontWeight = FontWeight.Bold)
                }

                else -> OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    border = BorderStroke(1.dp, GreenActive.copy(alpha = 0.60f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenActive),
                    shape = AgapShape.md
                ) {
                    Text("RESTORE ACCESS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UserDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = RedPrimary,
            letterSpacing = 0.8.sp
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerShape = AgapShape.lg),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(AgapSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                content = content
            )
        }
    }
}

@Composable
private fun UserDetailRow(
    label: String,
    value: String,
    maxLines: Int = 1
) {
    Column {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.45.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UserDetailItem(
    title: String,
    subtitle: String,
    detail: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.sm,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun String?.orNotProvided(): String = this?.takeIf { it.isNotBlank() } ?: "Not provided"

private fun String?.maskedValue(): String {
    val value = this?.takeIf { it.isNotBlank() } ?: return "Not provided"
    if (value.length <= 8) return "****"
    return "${value.take(4)}…${value.takeLast(4)}"
}

@Composable
private fun LegacyUserAccountCard(
    user: UserDto,
    onApprove: (() -> Unit)?,
    onReject: (() -> Unit)?,
    onSuspend: (() -> Unit)?,
    onRestore: (() -> Unit)?
) {
    val status = user.accountStatus.uppercase(Locale.ROOT)
    val statusColor = accountStatusColor(status)
    val roleIcon = if (user.role.equals("RESPONDER", true)) Icons.Outlined.LocalHospital else Icons.Outlined.Person

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerShape = AgapShape.xl,
                borderColor = statusColor.copy(alpha = 0.22f)
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AgapShape.md)
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.28f), AgapShape.md),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = roleIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${user.role.replace('_', ' ')} • ${user.displayId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(AgapSpacing.sm))
                StatusPill(text = status, color = statusColor)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            ContactLine(
                icon = Icons.Outlined.Phone,
                label = "PHONE",
                value = user.phoneNumber?.takeIf { it.isNotBlank() } ?: "Not provided"
            )
            ContactLine(
                icon = Icons.Outlined.Email,
                label = "EMAIL",
                value = user.email?.takeIf { it.isNotBlank() } ?: "Not provided"
            )

            if (!user.nationalId.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AgapShape.sm,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "National ID on file • ${user.nationalId.maskedIdentifier()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when (status) {
                "PENDING" -> {
                    Spacer(Modifier.height(AgapSpacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        Button(
                            onClick = { onApprove?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            enabled = onApprove != null,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenActive),
                            shape = AgapShape.md
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("APPROVE", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onReject?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            enabled = onReject != null,
                            border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                            shape = AgapShape.md
                        ) {
                            Text("REJECT", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "APPROVED" -> {
                    if (onSuspend != null) {
                        Spacer(Modifier.height(AgapSpacing.lg))
                        OutlinedButton(
                            onClick = onSuspend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                            shape = AgapShape.md
                        ) {
                            Text("SUSPEND ACCOUNT", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> {
                    Spacer(Modifier.height(AgapSpacing.md))
                    Text(
                        text = "Account access is currently restricted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    if (onRestore != null) {
                        Spacer(Modifier.height(AgapSpacing.md))
                        OutlinedButton(
                            onClick = onRestore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            border = BorderStroke(1.dp, GreenActive.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenActive),
                            shape = AgapShape.md
                        ) {
                            Text("RESTORE ACCESS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgencyManagementContent(
    agencies: List<AgencyDto>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AgencyDto) -> Unit,
    onDelete: (AgencyDto) -> Unit
) {
    val query =
        searchQuery.trim()

    val filtered =
        remember(
            agencies,
            query
        ) {
            agencies
                .filter { agency ->
                    query.isBlank() ||
                            agency.name
                                .contains(
                                    query,
                                    true
                                ) ||
                            agency.code
                                .contains(
                                    query,
                                    true
                                ) ||
                            agency.type
                                .contains(
                                    query,
                                    true
                                ) ||
                            agency.contactNumber
                                .contains(
                                    query,
                                    true
                                )
                }
                .sortedBy {
                    it.name.lowercase(
                        Locale.getDefault()
                    )
                }
        }

    var visibleLimit by
    rememberSaveable(query) {
        mutableIntStateOf(
            MANAGEMENT_LARGE_BATCH_SIZE
        )
    }

    val visibleAgencies =
        filtered.take(
            visibleLimit
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            ManagementOverview(
                eyebrow = "DISPATCH DIRECTORY",
                title = "Agency registry",
                subtitle = "Maintain organizations available for emergency routing and response.",
                metrics = listOf(
                    OverviewMetric("TOTAL", agencies.size, RedPrimary),
                    OverviewMetric("TYPES", agencies.map { it.type }.distinct().size, PaletteOrangeDark),
                    OverviewMetric("CONTACT", agencies.count { it.contactNumber.isNotBlank() }, GreenActive)
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManagementSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = "Search agencies",
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier.height(56.dp),
                    shape = AgapShape.md,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Outlined.Add, null)
                }
            }
        }

        item {
            ManagementSectionHeader(
                title = "Registered agencies",
                subtitle = if (query.isBlank()) "Organizations currently available to AGAP" else "Results for “$query”",
                count = filtered.size,
                accent = RedPrimary
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyManagementState(
                    title = if (query.isBlank()) "No agencies registered" else "No matching agencies",
                    message = if (query.isBlank()) {
                        "Add the first response agency to begin building the dispatch directory."
                    } else {
                        "Try searching by agency name, code, type or contact number."
                    }
                )
            }
        } else {
            items(
                items =
                    visibleAgencies,
                key = {
                    it.id
                },
                contentType = {
                    "agency_card"
                }
            ) { agency ->
                AgencyCard(
                    agency = agency,
                    onEdit = { onEdit(agency) },
                    onDelete = {
                        onDelete(agency)
                    }
                )
            }

            if (
                visibleAgencies.size <
                filtered.size
            ) {
                item {
                    ManagementLoadMore(
                        showing =
                            visibleAgencies.size,
                        total =
                            filtered.size,
                        onLoadMore = {
                            visibleLimit +=
                                MANAGEMENT_LARGE_BATCH_SIZE
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgencyCard(
    agency: AgencyDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = agencyTypeColor(agency.type)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AgapShape.md)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agency.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(text = agency.type, color = accent)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = agency.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit agency", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Delete agency", tint = RedPrimary)
                }
            }

            if (agency.contactNumber.isNotBlank()) {
                Spacer(Modifier.height(AgapSpacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(AgapSpacing.md))
                ContactLine(Icons.Outlined.Phone, "CONTACT", agency.contactNumber)
            }

            val serviceArea = listOf(agency.provinceCode, agency.muniCode, agency.barangayCode)
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            if (serviceArea.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SERVICE AREA  $serviceArea",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.35.sp
                )
            }
        }
    }
}

@Composable
private fun ResponderManagementContent(
    units: List<ResponderUnitDto>,
    agencies: List<AgencyDto>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ResponderUnitDto) -> Unit,
    onDelete: (ResponderUnitDto) -> Unit
) {
    val agencyById =
        remember(agencies) {
            agencies.associateBy {
                it.id
            }
        }

    val query =
        searchQuery.trim()

    val filtered =
        remember(
            units,
            agencyById,
            query
        ) {
            units
                .filter { unit ->
                    val agency =
                        agencyById[
                            unit.agencyId
                        ]

                    query.isBlank() ||
                            unit.unitCallsign
                                .contains(
                                    query,
                                    true
                                ) ||
                            unit.status
                                .contains(
                                    query,
                                    true
                                ) ||
                            unit.targetId
                                .contains(
                                    query,
                                    true
                                ) ||
                            agency?.name
                                .orEmpty()
                                .contains(
                                    query,
                                    true
                                ) ||
                            agency?.code
                                .orEmpty()
                                .contains(
                                    query,
                                    true
                                )
                }
                .sortedBy {
                    it.unitCallsign
                        .lowercase(
                            Locale.getDefault()
                        )
                }
        }

    val statusCounts =
        remember(units) {
            Triple(
                units.count {
                    it.status.equals(
                        "IDLE",
                        true
                    )
                },
                units.count {
                    it.status.equals(
                        "DISPATCHED",
                        true
                    )
                },
                units.count {
                    it.status.equals(
                        "OFFLINE",
                        true
                    )
                }
            )
        }

    val availableCount =
        statusCounts.first

    val activeCount =
        statusCounts.second

    val offlineCount =
        statusCounts.third

    var visibleLimit by
    rememberSaveable(query) {
        mutableIntStateOf(
            MANAGEMENT_LARGE_BATCH_SIZE
        )
    }

    val visibleUnits =
        filtered.take(
            visibleLimit
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AgapSpacing.lg,
            end = AgapSpacing.lg,
            top = AgapSpacing.lg,
            bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
    ) {
        item {
            ManagementOverview(
                eyebrow = "FIELD RESOURCES",
                title = "Responder units",
                subtitle = "Maintain the units available for dispatch and field response.",
                metrics = listOf(
                    OverviewMetric("AVAILABLE", availableCount, GreenActive),
                    OverviewMetric("ACTIVE", activeCount, PaletteOrangeDark),
                    OverviewMetric("OFFLINE", offlineCount, RedPrimary)
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManagementSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = "Search units or agencies",
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier.height(56.dp),
                    shape = AgapShape.md,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Outlined.Add, null)
                }
            }
        }

        item {
            ManagementSectionHeader(
                title = "Unit directory",
                subtitle = if (query.isBlank()) "Response assets registered with command" else "Results for “$query”",
                count = filtered.size,
                accent = PaletteOrangeDark
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyManagementState(
                    title = if (query.isBlank()) "No responder units registered" else "No matching units",
                    message = if (query.isBlank()) {
                        "Create a responder unit after registering its parent agency."
                    } else {
                        "Try a callsign, unit status, target ID or agency name."
                    }
                )
            }
        } else {
            items(
                items =
                    visibleUnits,
                key = {
                    it.id
                },
                contentType = {
                    "responder_unit_card"
                }
            ) { unit ->
                ResponderUnitCard(
                    unit = unit,
                    agency = agencyById[unit.agencyId],
                    onEdit = { onEdit(unit) },
                    onDelete = {
                        onDelete(unit)
                    }
                )
            }

            if (
                visibleUnits.size <
                filtered.size
            ) {
                item {
                    ManagementLoadMore(
                        showing =
                            visibleUnits.size,
                        total =
                            filtered.size,
                        onLoadMore = {
                            visibleLimit +=
                                MANAGEMENT_LARGE_BATCH_SIZE
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponderUnitCard(
    unit: ResponderUnitDto,
    agency: AgencyDto?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = responderStatusColor(unit.status)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AgapShape.md)
                        .background(statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.width(AgapSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.unitCallsign,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = agency?.let { "${it.code} • ${it.name}" } ?: "Agency #${unit.agencyId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(AgapSpacing.sm))
                StatusPill(unit.status, statusColor)
            }

            Spacer(Modifier.height(AgapSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(AgapSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TARGET ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = unit.targetId.takeIf { it.isNotBlank() }?.compactIdentifier() ?: "Generated automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit responder unit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Delete responder unit", tint = RedPrimary)
                }
            }

            if (unit.currentLat != 0.0 || unit.currentLon != 0.0) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "LAST POSITION  ${"%.5f".format(Locale.US, unit.currentLat)}, ${"%.5f".format(Locale.US, unit.currentLon)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class OverviewMetric(
    val label: String,
    val value: Int,
    val color: Color
)

@Composable
private fun ManagementOverview(
    eyebrow: String,
    title: String,
    subtitle: String,
    metrics: List<OverviewMetric>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.lg)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = RedPrimary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(AgapSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
            ) {
                metrics.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = AgapShape.md,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = metric.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = metric.value.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = AgapShape.md,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@Composable
private fun ManagementSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 0.55.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(AgapSpacing.sm))
        Surface(
            shape = AgapShape.pill,
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun EmptyManagementState(
    title: String,
    message: String,
    success: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.xl,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background((if (success) GreenActive else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (success) GreenActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactLine(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.sm)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.45.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Surface(
        shape = AgapShape.pill,
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = text.replace('_', ' ').uppercase(Locale.ROOT),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.35.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AgencyEditorDialog(
    isEditing: Boolean,
    agencyName: String,
    agencyCode: String,
    agencyType: String,
    agencyContact: String,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val canSave = agencyName.isNotBlank() && agencyCode.isNotBlank() && agencyType in agencyTypes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditing) "Edit agency" else "Add agency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dispatch directory record",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                OutlinedTextField(
                    value = agencyName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agency name") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = agencyCode,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agency code") },
                    placeholder = { Text("e.g. DRRMO-LEGAZPI") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Column {
                    Text(
                        text = "AGENCY TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.55.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        agencyTypes.forEach { type ->
                            FilterChip(
                                selected = agencyType == type,
                                onClick = { onTypeChange(type) },
                                label = { Text(type) },
                                shape = AgapShape.pill,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = agencyTypeColor(type).copy(alpha = 0.13f),
                                    selectedLabelColor = agencyTypeColor(type)
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = agencyContact,
                    onValueChange = onContactChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contact number") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text(if (isEditing) "SAVE CHANGES" else "ADD AGENCY", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ResponderEditorDialog(
    isEditing: Boolean,
    callsign: String,
    agencyId: Long,
    targetId: String,
    status: String,
    agencies: List<AgencyDto>,
    onCallsignChange: (String) -> Unit,
    onAgencyChange: (Long) -> Unit,
    onTargetIdChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val canSave = callsign.isNotBlank() && agencyId != 0L && agencies.any { it.id == agencyId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditing) "Edit responder unit" else "Add responder unit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Field dispatch resource",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
            ) {
                if (agencies.isEmpty()) {
                    Surface(
                        shape = AgapShape.md,
                        color = PaletteOrangeDark.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, PaletteOrangeDark.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = PaletteOrangeDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Create an agency before adding responder units.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = callsign,
                    onValueChange = onCallsignChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Unit callsign") },
                    placeholder = { Text("e.g. DRRMO-ALPHA-1") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                if (agencies.isNotEmpty()) {
                    Column {
                        Text(
                            text = "ASSIGNED AGENCY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.55.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            agencies.forEach { agency ->
                                FilterChip(
                                    selected = agencyId == agency.id,
                                    onClick = { onAgencyChange(agency.id) },
                                    label = {
                                        Text(
                                            agency.code,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    shape = AgapShape.pill
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetId,
                    onValueChange = onTargetIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Target spatial ID (optional)") },
                    supportingText = { Text("Leave blank to let AGAP generate one.") },
                    singleLine = true,
                    shape = AgapShape.md,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                if (isEditing) {
                    Column {
                        Text(
                            text = "UNIT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.55.sp
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            responderStatuses.forEach { value ->
                                FilterChip(
                                    selected = status == value,
                                    onClick = { onStatusChange(value) },
                                    label = { Text(value) },
                                    shape = AgapShape.pill,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = responderStatusColor(value).copy(alpha = 0.13f),
                                        selectedLabelColor = responderStatusColor(value)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Text(if (isEditing) "SAVE CHANGES" else "ADD UNIT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ConfirmationDialog(
    request: ConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (request.destructive) RedPrimary else PaletteOrangeDark
            )
        },
        title = {
            Text(
                text = request.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = request.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (request.destructive) RedPrimary else PaletteOrangeDark
                )
            ) {
                Text(request.confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private fun UserDto.displayName(): String = name?.takeIf { it.isNotBlank() } ?: username

private fun UserDto.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return displayName().contains(query, true) ||
            username.contains(query, true) ||
            role.contains(query, true) ||
            displayId.contains(query, true) ||
            phoneNumber.orEmpty().contains(query, true) ||
            email.orEmpty().contains(query, true)
}

private fun String.maskedIdentifier(): String {
    if (isBlank()) return ""
    val visible = takeLast(minOf(4, length))
    return "•••• $visible"
}

private fun String.compactIdentifier(): String {
    if (length <= 24) return this
    return "${take(10)}…${takeLast(8)}"
}

private fun accountStatusColor(status: String): Color = when (status.uppercase(Locale.getDefault())) {
    "APPROVED" -> GreenActive
    "PENDING" -> PaletteOrangeDark
    "SUSPENDED", "REJECTED" -> RedPrimary
    else -> MaterialThemeFallbackGray
}

private fun responderStatusColor(status: String): Color = when (status.uppercase(Locale.getDefault())) {
    "IDLE" -> GreenOnline
    "DISPATCHED" -> PaletteOrangeDark
    "OFFLINE" -> RedPrimary
    else -> MaterialThemeFallbackGray
}

private fun agencyTypeColor(type: String): Color = when (type.uppercase(Locale.getDefault())) {
    "MEDICAL" -> Color(0xFF1F91A8)
    "FIRE" -> RedPrimary
    "POLICE" -> Color(0xFF6E82D8)
    "RESCUE" -> PaletteOrangeDark
    "DRRMO" -> Color(0xFF2EB872)
    "VOLUNTEER" -> Color(0xFF8B6CCF)
    else -> MaterialThemeFallbackGray
}

private val MaterialThemeFallbackGray = Color(0xFF8D9297)
