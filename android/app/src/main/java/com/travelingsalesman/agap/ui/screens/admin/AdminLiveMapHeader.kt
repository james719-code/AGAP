package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.GreenOnline

@Composable
internal fun LiveMapHeaderV2(
    activeCount: Int,
    responderCount: Int,
    isOnline: Boolean,
    onBack: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DarkRoundButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            onClick = onBack
        )

        Spacer(Modifier.width(AgapSpacing.md))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live map",
                    color = MapText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )

                Spacer(Modifier.width(AgapSpacing.sm))

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) {
                                GreenOnline
                            } else {
                                AmberWarning
                            }
                        )
                )
            }

            Text(
                text = "Camarines Sur • $activeCount active • $responderCount units",
                color = MapTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DarkRoundButton(
            icon = Icons.Outlined.FilterAlt,
            contentDescription = "Filters",
            onClick = onFilter
        )
    }
}

@Composable
internal fun DarkRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MapPanel,
        border = BorderStroke(
            1.dp,
            MapBorder
        ),
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MapText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun LiveStatusChipV2(
    isOnline: Boolean,
    activeCount: Int,
    responderCount: Int,
    modifier: Modifier = Modifier
) {
    val accent = if (isOnline) {
        GreenOnline
    } else {
        AmberWarning
    }

    Surface(
        modifier = modifier,
        shape = AgapShape.pill,
        color = MapPanelSoft,
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.42f)
        ),
        shadowElevation = 2.dp
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
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )

            Spacer(Modifier.width(AgapSpacing.sm))

            Text(
                text = if (isOnline) {
                    "Live · $activeCount incidents · $responderCount units"
                } else {
                    "Offline · $activeCount saved incidents"
                },
                color = MapText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
        }
    }
}
