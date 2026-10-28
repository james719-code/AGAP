package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.R
import com.travelingsalesman.agap.ui.theme.AgapSpacing

/** The cropped official wordmark, with the same proportions on every screen. */
@Composable
fun AgapBrand(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    subtitle: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
    ) {
        Image(
            painter = painterResource(R.drawable.agap_logo_name),
            contentDescription = "AGAP PH",
            modifier = Modifier.width(if (compact) 104.dp else 156.dp)
                .height(if (compact) 26.dp else 39.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
