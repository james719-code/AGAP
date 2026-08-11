package com.travelingsalesman.agap.ui.screens.incidents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.R
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.IncidentCard
import com.travelingsalesman.agap.ui.components.IncidentCardVariant
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.BlueMedical
import com.travelingsalesman.agap.ui.theme.GreenOnline
import com.travelingsalesman.agap.ui.theme.GreenResolved
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (incidentId: String) -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: IncidentListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        uiState.selectedStatusFilter,
        uiState.selectedTypeFilter
    ).size + if (uiState.searchQuery.isNotBlank()) 1 else 0

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "INCIDENT FEED",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack,
                onNotificationClick = onNavigateToNotifications,
                onMessageClick = onNavigateToMessages
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (
                (uiState.isLoading ||
                        uiState.isFiltering) &&
                uiState.totalIncidentCount > 0
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = RedPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            }

            if (uiState.liveSyncState.name == "STALE" || uiState.liveSyncState.name == "OFFLINE") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = AgapSpacing.lg,
                        vertical = AgapSpacing.sm
                    ),
                    color = AmberWarning.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f)),
                    shape = AgapShape.md
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = AgapSpacing.md,
                            vertical = AgapSpacing.sm
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.sync_stale),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = viewModel::refreshRemoteIncidents) {
                            Text("RESYNC", color = RedPrimary)
                        }
                    }
                }
            }

            val listContent = @Composable {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AgapSpacing.lg,
                        end = AgapSpacing.lg,
                        top = AgapSpacing.md,
                        bottom = AgapSpacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                ) {
                    item {
                        IncidentSearchAndFilter(
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            selectedStatus = uiState.selectedStatusFilter,
                            onStatusSelected = viewModel::setStatusFilter,
                            activeFilterCount = activeFilterCount,
                            showAdvancedFilters = showAdvancedFilters,
                            onToggleAdvancedFilters = {
                                showAdvancedFilters = !showAdvancedFilters
                            }
                        )
                    }

                    if (showAdvancedFilters) {
                        item {
                            AdvancedIncidentFilters(
                                selectedStatus = uiState.selectedStatusFilter,
                                selectedType = uiState.selectedTypeFilter,
                                onStatusSelected = viewModel::setStatusFilter,
                                onTypeSelected = viewModel::setTypeFilter,
                                onClearAll = viewModel::clearFilters
                            )
                        }
                    }

                    item {
                        IncidentSectionHeader(
                            visibleCount = uiState.totalFilteredCount,
                            totalCount = uiState.totalIncidentCount,
                            hasFilters = activeFilterCount > 0,
                            onClearFilters = viewModel::clearFilters
                        )
                    }

                    when {
                        uiState.isLoading &&
                                uiState.totalIncidentCount == 0 -> {
                            item { InitialFeedLoadingState() }
                        }

                        uiState.incidents.isEmpty() -> {
                            item {
                                EmptyIncidentState(
                                    hasFilters = activeFilterCount > 0,
                                    onClearFilters = viewModel::clearFilters
                                )
                            }
                        }

                        else -> {
                            items(
                                items =
                                    uiState.incidents,
                                key = {
                                    it.incidentId
                                },
                                contentType = {
                                    "incident_card"
                                }
                            ) { incident ->
                                IncidentCard(
                                    incident = incident,
                                    onClick = {
                                        onNavigateToDetail(
                                            incident.incidentId
                                        )
                                    },
                                    variant = IncidentCardVariant.Compact
                                )
                            }

                            if (
                                uiState.canLoadMore
                            ) {
                                item(
                                    key =
                                        "incident-load-more",
                                    contentType =
                                        "load_more"
                                ) {
                                    IncidentLoadMoreCard(
                                        visibleCount =
                                            uiState
                                                .incidents
                                                .size,
                                        totalCount =
                                            uiState
                                                .totalFilteredCount,
                                        onLoadMore =
                                            viewModel::loadMore
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshRemoteIncidents,
                modifier = Modifier.fillMaxSize()
            ) {
                listContent()
            }
        }
    }
}

@Composable
private fun IncidentOperationsSummary(
    totalCount: Int,
    needsActionCount: Int,
    activeResponseCount: Int,
    resolvedCount: Int,
    isRefreshing: Boolean,
    lastRefreshAt: Long?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .padding(AgapSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Operations Feed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(AgapSpacing.xs))
                Text(
                    text = if (lastRefreshAt == null) {
                        "Local cache with live incident synchronization"
                    } else {
                        "Last synced ${formatLastRefresh(lastRefreshAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = AgapShape.pill,
                color = if (isRefreshing) {
                    AmberWarning.copy(alpha = 0.12f)
                } else {
                    GreenOnline.copy(alpha = 0.12f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isRefreshing) {
                        AmberWarning.copy(alpha = 0.35f)
                    } else {
                        GreenOnline.copy(alpha = 0.35f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AgapSpacing.md,
                        vertical = AgapSpacing.sm
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isRefreshing) AmberWarning else GreenOnline)
                    )
                    Spacer(modifier = Modifier.width(AgapSpacing.sm))
                    Text(
                        text = if (isRefreshing) "SYNCING" else "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRefreshing) AmberWarning else GreenOnline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AgapSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            IncidentMetric(
                modifier = Modifier.weight(1f),
                label = "ACTION",
                value = needsActionCount,
                subtitle = "Needs review",
                icon = Icons.Outlined.WarningAmber,
                accent = RedPrimary
            )
            IncidentMetric(
                modifier = Modifier.weight(1f),
                label = "ACTIVE",
                value = activeResponseCount,
                subtitle = "Responding",
                icon = Icons.Outlined.LocalShipping,
                accent = AmberWarning
            )
            IncidentMetric(
                modifier = Modifier.weight(1f),
                label = "RESOLVED",
                value = resolvedCount,
                subtitle = "$totalCount total",
                icon = Icons.Outlined.CheckCircle,
                accent = GreenResolved
            )
        }
    }
}

@Composable
private fun IncidentMetric(
    label: String,
    value: Int,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard(
                cornerShape = AgapShape.lg,
                borderColor = MaterialTheme.colorScheme.surfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
            )
            .padding(AgapSpacing.md)
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(AgapShape.sm)
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(AgapSpacing.sm))
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(AgapSpacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.55.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
    }
}

@Composable
private fun IncidentSearchAndFilter(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: String?,
    onStatusSelected: (String?) -> Unit,
    activeFilterCount: Int,
    showAdvancedFilters: Boolean,
    onToggleAdvancedFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = AgapShape.lg,
                placeholder = {
                    Text(
                        text = "Search incident, type, agency...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = RedPrimary.copy(alpha = 0.75f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClick = onToggleAdvancedFilters),
                shape = AgapShape.lg,
                color = if (showAdvancedFilters || activeFilterCount > 0) {
                    RedPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (showAdvancedFilters || activeFilterCount > 0) {
                        RedPrimary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "Show filters",
                        tint = if (showAdvancedFilters || activeFilterCount > 0) {
                            RedPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = AgapSpacing.sm, end = AgapSpacing.sm)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeFilterCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            QuickStatusChip(
                label = "ALL",
                selected = selectedStatus == null,
                onClick = { onStatusSelected(null) }
            )
            listOf("RECEIVED", "DISPATCHED", "EN_ROUTE", "RESOLVED").forEach { status ->
                QuickStatusChip(
                    label = status.replace("_", " "),
                    selected = selectedStatus.equals(status, ignoreCase = true),
                    onClick = { onStatusSelected(status) }
                )
            }
        }
    }
}

@Composable
private fun QuickStatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = AgapShape.pill,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = RedPrimary.copy(alpha = 0.14f),
            selectedLabelColor = RedPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedBorderColor = RedPrimary.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun AdvancedIncidentFilters(
    selectedStatus: String?,
    selectedType: String?,
    onStatusSelected: (String?) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onClearAll: () -> Unit
) {
    val statuses = listOf(
        "RECEIVED",
        "ACKNOWLEDGED",
        "DISPATCHED",
        "EN_ROUTE",
        "ON_SCENE",
        "RESOLVED"
    )
    val types = listOf("MEDICAL", "CRIME", "ACCIDENT", "DISASTER", "FIRE")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .padding(AgapSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Advanced Filters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Combine a response status with an emergency type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedStatus != null || selectedType != null) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "CLEAR",
                        color = RedPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AgapSpacing.md))
        FilterRow(
            title = "STATUS",
            values = statuses,
            selectedValue = selectedStatus,
            onSelected = onStatusSelected
        )

        Spacer(modifier = Modifier.height(AgapSpacing.md))
        FilterRow(
            title = "TYPE",
            values = types,
            selectedValue = selectedType,
            onSelected = onTypeSelected
        )
    }
}

@Composable
private fun FilterRow(
    title: String,
    values: List<String>,
    selectedValue: String?,
    onSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp
        )
        Spacer(modifier = Modifier.height(AgapSpacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
        ) {
            values.forEach { value ->
                val selected = selectedValue.equals(value, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { onSelected(value) },
                    label = {
                        Text(
                            text = value.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = AgapShape.pill,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        selectedContainerColor = RedPrimary.copy(alpha = 0.14f),
                        selectedLabelColor = RedPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedBorderColor = RedPrimary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
private fun IncidentSectionHeader(
    visibleCount: Int,
    totalCount: Int,
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AgapSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "INCIDENT QUEUE",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.55.sp
            )
            Text(
                text = if (hasFilters) {
                    "$visibleCount of $totalCount incidents match the current view"
                } else {
                    "Prioritized by response state, then newest first"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasFilters) {
            TextButton(onClick = onClearFilters) {
                Text(
                    text = "RESET",
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Surface(
                shape = AgapShape.pill,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = visibleCount.toString(),
                    modifier = Modifier.padding(
                        horizontal = AgapSpacing.md,
                        vertical = AgapSpacing.sm
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun IncidentLoadMoreCard(
    visibleCount: Int,
    totalCount: Int,
    onLoadMore: () -> Unit
) {
    OutlinedButton(
        onClick = onLoadMore,
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.md,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(
            vertical = AgapSpacing.md,
            horizontal = AgapSpacing.lg
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing $visibleCount of $totalCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                Text(
                    text = "LOAD MORE",
                    style = MaterialTheme.typography.labelLarge,
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun InitialFeedLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .glassCard(cornerShape = AgapShape.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = RedPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.height(AgapSpacing.lg))
            Text(
                text = "Loading incident feed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(AgapSpacing.xs))
            Text(
                text = "Syncing command data and local cache...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyIncidentState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerShape = AgapShape.xl)
            .padding(horizontal = AgapSpacing.xxl, vertical = AgapSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(BlueMedical.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Outlined.Search else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (hasFilters) BlueMedical else GreenResolved,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.height(AgapSpacing.lg))
        Text(
            text = if (hasFilters) "No matching incidents" else "No incidents in the feed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(AgapSpacing.xs))
        Text(
            text = if (hasFilters) {
                "Try clearing one of the filters or use a broader search term."
            } else {
                "New emergency reports will appear here as soon as they are received."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AgapSpacing.md))
        if (hasFilters) {
            TextButton(onClick = onClearFilters) {
                Text(
                    text = "CLEAR FILTERS",
                    color = RedPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatLastRefresh(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}
