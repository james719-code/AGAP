package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing

@Composable
fun AgapAuthLayout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AgapSpacing.lg, vertical = AgapSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.lg)
        ) {
            AgapBrand(compact = false)
            Spacer(Modifier.height(AgapSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm)) {
                    Box(Modifier.size(8.dp).background(colors.primary, AgapShape.circle))
                    Text(
                        "Community safety network",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Text(description, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(AgapSpacing.xs))
            content()
        }
    }
}

@Composable
fun AgapAuthForm(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md),
            content = content
        )
    }
}

@Composable
fun agapAuthFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = colors.outlineVariant,
        unfocusedContainerColor = colors.surface,
        focusedContainerColor = colors.surface
    )
}

@Composable
fun AgapAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        singleLine = true,
        shape = AgapShape.md,
        modifier = Modifier.fillMaxWidth(),
        colors = agapAuthFieldColors()
    )
}

@Composable
fun AgapAuthPassword(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    visibilityLabel: String = "password",
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(enabled = enabled, onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide $visibilityLabel" else "Show $visibilityLabel"
                )
            }
        },
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = AgapShape.md,
        modifier = Modifier.fillMaxWidth(),
        colors = agapAuthFieldColors()
    )
}

@Composable
fun AgapAuthAction(label: String, loadingLabel: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = AgapShape.pill
    ) {
        Text(if (loading) loadingLabel else label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
fun AgapAuthError(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = AgapShape.sm,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(AgapSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AgapSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
