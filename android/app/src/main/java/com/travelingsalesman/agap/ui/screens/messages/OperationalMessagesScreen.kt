package com.travelingsalesman.agap.ui.screens.messages

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import com.travelingsalesman.agap.ui.screens.admin.BroadcastAlertScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.travelingsalesman.agap.ui.components.AgapPageIntro
import com.travelingsalesman.agap.ui.components.AgapTopBar
import com.travelingsalesman.agap.ui.components.StatusBadge
import com.travelingsalesman.agap.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalMessagesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToIncidents: () -> Unit,
    onNavigateToChat: ((incidentId: String?, contactId: String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: OperationalMessagesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isAdmin = uiState.userRole.uppercase() == "ADMIN"
    val isResponder = uiState.userRole.uppercase() == "RESPONDER"

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = if (isAdmin) listOf("RESPONDERS", "BROADCAST") else emptyList()

    Scaffold(
        topBar = {
            AgapTopBar(
                title = when {
                    isAdmin -> "RESPONDER CHANNELS"
                    isResponder -> "MY COMMAND CHANNELS"
                    else -> "OPERATIONAL MESSAGES"
                },
                showBackButton = showBackButton,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isAdmin) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = bgColor,
                    contentColor = RedPrimary,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                            color = RedPrimary
                        )
                    },
                    divider = { HorizontalDivider(color = borderCol.copy(alpha = 0.5f)) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTabIndex == index) RedPrimary else subTextColor
                                )
                            }
                        )
                    }
                }
            }

            if (isAdmin && selectedTabIndex == 1) {
                BroadcastAlertScreen()
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AgapSpacing.lg)
                    ) {
                        AgapPageIntro(
                            title = if (isAdmin) "Field responder channels" else "Your assignments",
                            description = if (isAdmin) {
                                "Open an incident to review its timeline and send operational updates."
                            } else if (isResponder) {
                                "Open an assigned incident to read Command updates and send a reply."
                            } else {
                                "Operational messaging is available to command and field responders."
                            },
                            modifier = Modifier.padding(top = AgapSpacing.lg)
                        )

                        Spacer(modifier = Modifier.height(AgapSpacing.md))

                        if (uiState.contacts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = AgapSpacing.section + AgapSpacing.md),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = when {
                                        isResponder -> "No active assignments. New assignments from Command will appear here automatically."
                                        isAdmin -> "No field responder channels are available."
                                        else -> "No operational channels are available for this account."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = subTextColor
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
                            ) {
                                items(uiState.contacts, key = { it.id }) { contact ->
                                    OperationalContactCard(
                                        contact = contact,
                                        cardBg = cardBg,
                                        borderCol = borderCol,
                                        textColor = textColor,
                                        subTextColor = subTextColor,
                                        onClick = {
                                            if (onNavigateToChat != null) {
                                                onNavigateToChat(contact.incidentId, contact.id)
                                            } else if (contact.incidentId != null) {
                                                onNavigateToDetail(contact.incidentId)
                                            } else {
                                                onNavigateToIncidents()
                                            }
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
}

@Composable
private fun OperationalContactCard(
    contact: OperationalContactItem,
    cardBg: Color,
    borderCol: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AgapShape.md)
            .border(width = 1.dp, color = borderCol, shape = AgapShape.md)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (contact.role == "COMMAND") Icons.Outlined.Shield else Icons.Outlined.Person,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AgapSpacing.md))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (contact.incidentType != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${contact.incidentType})",
                                style = MaterialTheme.typography.labelSmall,
                                color = RedPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = contact.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(AgapSpacing.xs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = contact.status)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Open Chat",
                    tint = subTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
