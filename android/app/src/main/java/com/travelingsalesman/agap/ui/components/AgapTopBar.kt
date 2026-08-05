package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.RedPrimary
import com.travelingsalesman.agap.ui.theme.SlateBackground
import com.travelingsalesman.agap.ui.theme.SlateTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgapTopBar(
    title: String = "AGAP DISPATCH",
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SlateTextPrimary
                    )
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "AGAP Logo",
                        tint = RedPrimary
                    )
                }
            }
        },
        actions = {
            actions()
            onProfileClick?.let { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = SlateTextPrimary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SlateBackground
        )
    )
}
