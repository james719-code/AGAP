package com.travelingsalesman.agap.ui.screens.sent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.PremiumIncidentCard
import com.travelingsalesman.agap.ui.components.PremiumPendingPacketCard
import com.travelingsalesman.agap.ui.theme.*

enum class SentTab(val title: String) {
    SENT("MY SOS"),
    RELAYED("RELAYED"),
    RECEIVED("INCIDENT FEED")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentMessagesScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: SentMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(SentTab.SENT) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            AgapTopBar(
                title = "ALERTS & TELEMETRY",
                showBackButton = showBackButton,
                onBackClick = onNavigateBack,
                showStatusDot = true
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AgapSpacing.lg)
            ) {
                // Outbox Header Statistics Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AgapShape.lg)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), AgapShape.lg),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = OrangeAccident,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "MESSAGE TELEMETRY METRICS",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricItem("My SOS", "${uiState.sentPackets.size}", RedPrimary)
                            MetricItem("Relayed", "${uiState.relayedPackets.size}", BlueMedical)
                            MetricItem("Received", "${uiState.receivedIncidents.size}", GreenOnline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                // Shared Search and Filter Area
                AlertsSearchAndFilter(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    selectedStatus = uiState.selectedStatusFilter,
                    onStatusSelected = viewModel::setStatusFilter,
                    activeFilterCount = (if (uiState.searchQuery.isNotBlank()) 1 else 0) + (if (uiState.selectedStatusFilter != null) 1 else 0) + (if (uiState.selectedTypeFilter != null) 1 else 0),
                    showAdvancedFilters = showAdvancedFilters,
                    onToggleAdvancedFilters = { showAdvancedFilters = !showAdvancedFilters }
                )

                AnimatedVisibility(visible = showAdvancedFilters) {
                    AdvancedAlertsFilters(
                        selectedStatus = uiState.selectedStatusFilter,
                        selectedType = uiState.selectedTypeFilter,
                        onStatusSelected = viewModel::setStatusFilter,
                        onTypeSelected = viewModel::setTypeFilter,
                        onClearAll = viewModel::clearFilters
                    )
                }

                Spacer(modifier = Modifier.height(AgapSpacing.md))

                // Primary Tab Row
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = bgColor,
                    contentColor = RedPrimary,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                            color = RedPrimary,
                            width = 60.dp
                        )
                    },
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
                ) {
                    SentTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) RedPrimary else subTextColor,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTab) {
                    SentTab.SENT -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                        ) {
                            items(uiState.filteredSentPackets, key = { "sent_${it.packetHash}" }) { packet ->
                                PremiumPendingPacketCard(
                                    packet = packet,
                                    onBroadcast = { viewModel.broadcastViaMesh(packet) },
                                    onDelete = { viewModel.deletePendingPacket(packet.packetHash) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                    SentTab.RELAYED -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                        ) {
                            items(uiState.filteredRelayedPackets, key = { "relayed_${it.packetHash}" }) { packet ->
                                PremiumPendingPacketCard(
                                    packet = packet,
                                    onBroadcast = { viewModel.broadcastViaMesh(packet) },
                                    onDelete = { viewModel.deletePendingPacket(packet.packetHash) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                    SentTab.RECEIVED -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.filteredIncidents, key = { "received_${it.incidentId}" }) { incident ->
                                PremiumIncidentCard(
                                    incident = incident,
                                    onClick = {
                                        onNavigateToDetail(incident.incidentId)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(AgapSpacing.lg)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsSearchAndFilter(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatus: String?,
    onStatusSelected: (String?) -> Unit,
    activeFilterCount: Int,
    showAdvancedFilters: Boolean,
    onToggleAdvancedFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = AgapShape.xl,
                placeholder = {
                    Text(
                        text = "Search incidents...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1C1E).copy(alpha = 0.5f),
                    unfocusedContainerColor = Color(0xFF1A1C1E).copy(alpha = 0.3f),
                    focusedBorderColor = RedPrimary.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
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
                    Color(0xFF1A1C1E).copy(alpha = 0.3f)
                },
                border = BorderStroke(
                    1.dp,
                    if (showAdvancedFilters || activeFilterCount > 0) {
                        RedPrimary.copy(alpha = 0.5f)
                    } else {
                        Color.White.copy(alpha = 0.1f)
                    }
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
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
                                .padding(top = 7.dp, end = 7.dp)
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
    }
}

@Composable
private fun AdvancedAlertsFilters(
    selectedStatus: String?,
    selectedType: String?,
    onStatusSelected: (String?) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onClearAll: () -> Unit
) {
    val statuses = listOf("RECEIVED", "ACKNOWLEDGED", "DISPATCHED", "EN_ROUTE", "RESOLVED")
    val types = listOf("MEDICAL", "CRIME", "ACCIDENT", "DISASTER", "FIRE")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Color(0xFF1A1C1E).copy(alpha = 0.5f), AgapShape.xl)
            .border(1.dp, Color.White.copy(alpha = 0.05f), AgapShape.xl)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Filters",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClearAll) {
                Text("CLEAR ALL", color = RedPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FilterChipsRow(title = "STATUS", items = statuses, selected = selectedStatus, onSelected = onStatusSelected)
        Spacer(modifier = Modifier.height(12.dp))
        FilterChipsRow(title = "EMERGENCY TYPE", items = types, selected = selectedType, onSelected = onTypeSelected)
    }
}

@Composable
private fun FilterChipsRow(
    title: String,
    items: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                val isSelected = selected.equals(item, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(if (isSelected) null else item) },
                    label = { Text(item.replace("_", " "), fontSize = 10.sp) },
                    shape = AgapShape.pill,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = RedPrimary.copy(alpha = 0.15f),
                        selectedLabelColor = RedPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.White.copy(alpha = 0.1f),
                        selectedBorderColor = RedPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}
