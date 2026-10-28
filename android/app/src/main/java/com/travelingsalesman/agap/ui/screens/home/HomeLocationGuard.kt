package com.travelingsalesman.agap.ui.screens.home

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.AmberWarning
import com.travelingsalesman.agap.ui.theme.RedPrimary

@Composable
internal fun SosLocationGuardBanner(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onFixLocation: () -> Unit
) {
    val accent = if (!hasLocationPermission) AmberWarning else RedPrimary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFixLocation),
        shape = AgapShape.lg,
        color = accent.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AgapSpacing.md,
                vertical = AgapSpacing.md
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.size(AgapSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!hasLocationPermission) {
                        "LOCATION ACCESS REQUIRED"
                    } else {
                        "GPS IS OFF"
                    },
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )

                Text(
                    text = if (!hasLocationPermission) {
                        "Allow location access before the SOS joystick can be armed."
                    } else if (!gpsEnabled) {
                        "Turn on GPS so AGAP can attach your position to the emergency alert."
                    } else {
                        "Location is being prepared."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "FIX",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
internal fun SosJoystickLocationLock(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.46f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = AgapShape.xl,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            border = BorderStroke(
                1.dp,
                if (!hasLocationPermission) {
                    AmberWarning.copy(alpha = 0.55f)
                } else {
                    RedPrimary.copy(alpha = 0.55f)
                }
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.padding(horizontal = AgapSpacing.section)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AgapSpacing.xl,
                    vertical = AgapSpacing.lg
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (!hasLocationPermission) {
                                AmberWarning.copy(alpha = 0.12f)
                            } else {
                                RedPrimary.copy(alpha = 0.12f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = if (!hasLocationPermission) AmberWarning else RedPrimary,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.height(AgapSpacing.sm))

                Text(
                    text = if (!hasLocationPermission) {
                        "LOCATION ACCESS NEEDED"
                    } else {
                        "ENABLE GPS TO ARM SOS"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(AgapSpacing.xs))

                Text(
                    text = if (!hasLocationPermission) {
                        "Tap here to allow location access."
                    } else if (!gpsEnabled) {
                        "Tap here to open the Android GPS setting."
                    } else {
                        "Preparing location services."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun LocationRequirementDialog(
    hasLocationPermission: Boolean,
    gpsEnabled: Boolean,
    permissionWasRequested: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val needsPermission = !hasLocationPermission

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (needsPermission) {
                            AmberWarning.copy(alpha = 0.12f)
                        } else {
                            RedPrimary.copy(alpha = 0.12f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = if (needsPermission) AmberWarning else RedPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }
        },
        title = {
            Text(
                text = when {
                    needsPermission && permissionWasRequested ->
                        "Location permission is blocked"
                    needsPermission ->
                        "Allow location for SOS"
                    !gpsEnabled ->
                        "Turn on GPS for SOS"
                    else ->
                        "Location ready"
                },
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = when {
                    needsPermission && permissionWasRequested ->
                        "AGAP still does not have location access. Open App Settings and allow location so the SOS joystick can attach your position."
                    needsPermission ->
                        "AGAP uses your location to help responders identify where the emergency is happening. Allow location access before arming the SOS joystick."
                    !gpsEnabled ->
                        "GPS is currently off. Turn on Location/GPS in Android settings before choosing and sending an emergency alert."
                    else ->
                        "Location services are ready for emergency use."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = when {
                        needsPermission && permissionWasRequested ->
                            "OPEN APP SETTINGS"
                        needsPermission ->
                            "ALLOW LOCATION"
                        !gpsEnabled ->
                            "ENABLE GPS"
                        else ->
                            "DONE"
                    },
                    color = if (needsPermission) AmberWarning else RedPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            if (needsPermission || !gpsEnabled) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "NOT NOW",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

internal fun hasAnyLocationPermission(context: Context): Boolean {
    if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return true
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse
}

internal fun isGpsProviderEnabled(context: Context): Boolean {
    if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return true
    return try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val masterLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            true
        }

        masterLocationEnabled &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: Exception) {
        false
    }
}
