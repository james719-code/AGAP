@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.data.remote.dto.UserDto
import com.travelingsalesman.agap.ui.components.AgapPageIntro
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.GreenActive
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.glassCard
import java.util.Locale

@Composable
internal fun UserManagementContent(
    users: List<UserDto>,
    currentUserId: Long?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onUserClick: (UserDto) -> Unit
) {
    val managedUsers = remember(users) {
        users
    }

    val pending = remember(managedUsers) {
        managedUsers.filter {
            (it.accountStatus.equals("PENDING", true) || it.accountStatus.isBlank()) && !it.isVerified
        }
    }

    val approved = remember(managedUsers) {
        managedUsers.filter {
            it.accountStatus.equals("APPROVED", true) ||
            it.accountStatus.equals("ACTIVE", true) ||
            it.accountStatus.equals("VERIFIED", true) ||
            it.isVerified
        }
    }

    val restricted = remember(managedUsers) {
        managedUsers.filter {
            it.accountStatus.equals("REJECTED", true) ||
            it.accountStatus.equals("SUSPENDED", true)
        }
    }

    val query = searchQuery.trim()

    val filteredPending = remember(pending, query) {
        pending
            .filter { it.matches(query) }
            .sortedBy { it.displayName().lowercase(Locale.getDefault()) }
    }

    val filteredApproved = remember(approved, query) {
        approved
            .filter { it.matches(query) }
            .sortedBy { it.displayName().lowercase(Locale.getDefault()) }
    }

    val filteredRestricted = remember(restricted, query) {
        restricted
            .filter { it.matches(query) }
            .sortedBy { it.displayName().lowercase(Locale.getDefault()) }
    }

    var pendingLimit by rememberSaveable(query) { mutableIntStateOf(MANAGEMENT_BATCH_SIZE) }
    var approvedLimit by rememberSaveable(query) { mutableIntStateOf(MANAGEMENT_BATCH_SIZE) }
    var restrictedLimit by rememberSaveable(query) { mutableIntStateOf(MANAGEMENT_BATCH_SIZE) }

    val visiblePending = filteredPending.take(pendingLimit)
    val visibleApproved = filteredApproved.take(approvedLimit)
    val visibleRestricted = filteredRestricted.take(restrictedLimit)

    val visibleCount = filteredPending.size + filteredApproved.size + filteredRestricted.size
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
            AgapPageIntro("User directory", "Review account access and find registered users.")
        }
        item {
            ManagementSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = "Search users"
            )
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
            ) {
                listOf(
                    "ALL" to managedUsers.size,
                    "PENDING" to pending.size,
                    "APPROVED" to approved.size,
                    "RESTRICTED" to restricted.size
                ).forEach { (filterKey, count) ->
                    ManagementFilterChip(
                        selected = selectedStatusFilter == filterKey,
                        onClick = { selectedStatusFilter = filterKey },
                        label = "${filterKey.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }} ($count)"
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
                    items = visiblePending,
                    key = { "pending-${it.id}" },
                    contentType = { "pending_user" }
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
                    items = visibleApproved,
                    key = { "approved-${it.id}" },
                    contentType = { "approved_user" }
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
internal fun UserAccountCard(
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
            modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
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

            Spacer(Modifier.width(AgapSpacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)) {
                Text(
                    text = user.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Text(
                    text = "${user.role.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }} · ID ${user.displayId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                StatusPill(text = status, color = statusColor)
            }
        }
    }
}

@Composable
internal fun AdminUserDetailsContent(
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
                UserDetailGridRow(
                    UserDetail("Username", user.username),
                    UserDetail("Account ID", user.displayId)
                )
                UserDetailGridRow(
                    UserDetail("Role", user.role.replace('_', ' ')),
                    UserDetail("Verification", if (user.isVerified) "Verified" else "Not verified")
                )
                UserDetailGridRow(
                    UserDetail("Registration device", user.deviceId.maskedValue()),
                    UserDetail("Agency", user.agencyId?.let { "Agency #$it" }.orNotProvided())
                )
                UserDetailGridRow(
                    UserDetail("Address record", user.addressId?.let { "Address #$it" }.orNotProvided())
                )
            }
        }

        item {
            UserDetailSection("PROFILE & CONTACT") {
                UserDetailGridRow(
                    UserDetail("Phone", user.phoneNumber.orNotProvided()),
                    UserDetail("Email", user.email.orNotProvided())
                )
                UserDetailGridRow(
                    UserDetail("Birthday", user.birthday.orNotProvided()),
                    UserDetail("Gender", user.gender.orNotProvided())
                )
                UserDetailGridRow(
                    UserDetail("National ID", user.nationalId?.maskedValue().orNotProvided())
                )
                UserDetailGridRow(
                    UserDetail("Medical information", user.medicalInfo.orNotProvided(), maxLines = 4)
                )
                if (!user.imageUrl.isNullOrBlank()) {
                    UserDetailGridRow(UserDetail("Profile image", user.imageUrl, maxLines = 2))
                }
            }
        }

        item {
            UserDetailSection("ACTIVITY") {
                UserDetailGridRow(UserDetail("Last login", user.lastLoginAt.displayTimestamp()))
                UserDetailGridRow(
                    UserDetail("Account created", user.createdAt.displayTimestamp()),
                    UserDetail("Last updated", user.updatedAt.displayTimestamp())
                )
            }
        }

        item {
            UserDetailSection("REGISTERED DEVICES") {
                if (user.devices.isEmpty()) {
                    UserDetailGridRow(UserDetail("Devices", "No registered devices"))
                } else {
                    UserDetailGridRow(UserDetail("Registered devices", "${user.devices.size} device(s)"))
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
                    UserDetailGridRow(UserDetail("Emergency contacts", "No contacts on record"))
                } else {
                    UserDetailGridRow(UserDetail("Emergency contacts", "${user.emergencyContacts.size} saved"))
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
internal fun UserDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(15.dp)
                    .clip(AgapShape.pill)
                    .background(RedPrimary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.7.sp
            )
        }
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
internal fun UserDetailGridRow(
    first: UserDetail,
    second: UserDetail? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)
    ) {
        UserDetailCell(
            detail = first,
            modifier = if (second == null) Modifier.fillMaxWidth() else Modifier.weight(1f)
        )
        second?.let {
            UserDetailCell(
                detail = it,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun UserDetailCell(
    detail: UserDetail,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AgapShape.sm,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
        ) {
            Text(
                text = detail.label.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.45.sp
            )
            Text(
                text = detail.value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = detail.maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun UserDetailItem(
    title: String,
    subtitle: String,
    detail: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AgapShape.sm,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        Column(modifier = Modifier.padding(AgapSpacing.md)) {
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

@Composable
internal fun LegacyUserAccountCard(
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
                        modifier = Modifier.padding(horizontal = AgapSpacing.md, vertical = AgapSpacing.sm),
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

internal fun UserDto.displayName(): String = name?.takeIf { it.isNotBlank() } ?: username

internal fun UserDto.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return displayName().contains(query, true) ||
            username.contains(query, true) ||
            role.contains(query, true) ||
            displayId.contains(query, true) ||
            phoneNumber.orEmpty().contains(query, true) ||
            email.orEmpty().contains(query, true)
}

internal fun String?.orNotProvided(): String = this?.takeIf { it.isNotBlank() } ?: "Not provided"

internal fun String?.displayTimestamp(): String {
    val value = this?.takeIf { it.isNotBlank() } ?: return "Not recorded"
    return value
        .replace('T', ' ')
        .substringBefore('.')
        .removeSuffix("Z")
}

internal fun String?.maskedValue(): String {
    val value = this?.takeIf { it.isNotBlank() } ?: return "Not provided"
    if (value.length <= 8) return "****"
    return "${value.take(4)}…${value.takeLast(4)}"
}
