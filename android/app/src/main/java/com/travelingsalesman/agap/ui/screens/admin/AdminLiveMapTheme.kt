package com.travelingsalesman.agap.ui.screens.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * THEME-AWARE COMMAND MAP
 *
 * Do not use hard-coded dark/light surfaces here.
 * These getters read the currently active AGAP MaterialTheme, so the map UI
 * follows whatever theme AGAP is actually displaying.
 *
 * This is better than using isSystemInDarkTheme() directly because AGAPTheme
 * can be explicitly switched independently of the phone's system appearance.
 */
internal val MapPanel: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface.copy(
        alpha = 0.95f
    )

internal val MapPanelStrong: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface.copy(
        alpha = 0.985f
    )

internal val MapPanelSoft: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = 0.90f
    )

internal val MapBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

internal val MapText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

internal val MapTextMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

internal val MapBackdrop: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

internal val LIVE_MAP_CLOSED_STATUSES =
    setOf(
        "RESOLVED",
        "CANCELLED",
        "FALSE_ALARM"
    )

internal const val FULL_MAP_INCIDENT_LIMIT = 60
internal const val FULL_MAP_RESPONDER_LIMIT = 60
internal const val FULL_MAP_DISPATCH_LIMIT = 60
