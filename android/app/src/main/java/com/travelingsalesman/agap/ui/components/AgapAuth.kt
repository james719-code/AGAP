package com.travelingsalesman.agap.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.travelingsalesman.agap.ui.theme.AgapShape
import com.travelingsalesman.agap.ui.theme.AgapSpacing
import com.travelingsalesman.agap.ui.theme.glassCard

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
            AgapBrand(compact = true)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AgapShape.xl,
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(AgapSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgapSpacing.md)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AgapSpacing.xs)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                    AuthRouteMark(Modifier.size(72.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun AuthRouteMark(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val start = Offset(size.width * .13f, size.height * .76f)
        val relay = Offset(size.width * .47f, size.height * .58f)
        val end = Offset(size.width * .77f, size.height * .26f)
        val route = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(size.width * .34f, start.y, size.width * .30f, relay.y, relay.x, relay.y)
            cubicTo(size.width * .69f, relay.y, size.width * .56f, end.y, end.x, end.y)
        }
        drawPath(
            path = route,
            color = colors.onSecondaryContainer,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            )
        )
        drawCircle(colors.onSecondaryContainer, 3.dp.toPx(), start)
        drawCircle(colors.onSecondaryContainer, 3.dp.toPx(), relay)
        drawCircle(colors.onSecondaryContainer, 4.dp.toPx(), end)
        drawCircle(colors.onSecondaryContainer.copy(alpha = .7f), 9.dp.toPx(), end, style = Stroke(1.dp.toPx()))
        drawCircle(colors.onSecondaryContainer.copy(alpha = .35f), 15.dp.toPx(), end, style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun AgapAuthForm(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = Modifier.fillMaxWidth().glassCard().padding(AgapSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AgapSpacing.md),
            content = content
        )
    }
}

@Composable
fun agapAuthFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = colors.outline,
        unfocusedContainerColor = colors.surfaceContainerLow,
        focusedContainerColor = colors.surfaceContainerLow
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = AgapShape.md
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
