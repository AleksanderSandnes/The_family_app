@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.components

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.sandnes.familyapp.R
import com.sandnes.familyapp.ui.theme.BrandGradient
import com.sandnes.familyapp.ui.theme.Danger
import com.sandnes.familyapp.ui.theme.Elevation
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Primary gradient call-to-action button with built-in loading state. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    gradient: Brush = BrandGradient,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val active = enabled && !loading

    Box(
        modifier =
            modifier
                .scale(scale)
                .defaultMinSize(minHeight = 56.dp)
                // Disabled = the SAME brand fill at 38% opacity (Material standard) —
                // never a flat dead-gray, which reads as broken.
                .alpha(if (active) 1f else 0.38f)
                .clip(RoundedCornerShape(Radius.button))
                .background(gradient)
                .clickable(interactionSource = interaction, indication = null, enabled = active) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leadingIcon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Subtle outlined secondary action. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(Radius.button),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Destructive full-width action (Leave family, Sign out, Delete). Tonal red — not a loud
 *  gradient. Pairs with [ConfirmationDialog] for irreversible actions. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(Radius.button),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(it, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Standard extended FAB for the primary "create" action on list screens. */
@Composable
fun AppFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = { Text(text, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        // The label text isn't surfaced as the FAB's accessibility node, so set it
        // explicitly for screen readers (and UI test tooling).
        modifier = modifier.semantics { contentDescription = text },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Compact circular FAB for space-constrained screens (e.g. calendar, map). */
@Composable
fun AppFabSmall(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** Standard surface card for list rows. One radius, one elevation, one padding — the building
 *  block for every list screen (shopping, meals, birthdays, wishlists, family, etc.). */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    val inner: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Elevation.resting,
        ) { inner() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Elevation.resting,
        ) { inner() }
    }
}

/** Muted uppercase section label (settings groups, list sections). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Premium text field used across auth and forms. */
@Composable
fun FamilyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon =
            if (isPassword) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                }
            } else {
                null
            },
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            androidx.compose.foundation.text
                .KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(Radius.field),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Composable
fun InitialAvatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 44,
    avatarUri: String? = null,
    contentDescription: String? = name,
) {
    val desc = contentDescription
    var imageFailed by remember(avatarUri) { mutableStateOf(false) }
    if (avatarUri != null && !imageFailed) {
        AsyncImage(
            model = avatarUri,
            contentDescription = desc,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(CircleShape),
            onError = { imageFailed = true },
        )
    } else {
        Box(
            modifier =
                modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
                    .semantics { if (desc != null) this.contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Full-width centered loading spinner. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** Inline error banner that animates in/out. */
@Composable
fun ErrorBanner(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        ) {
            Text(
                message ?: "",
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun PillTag(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun CopyableCodeField(
    code: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.share_code),
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Filled tinted box with an eyebrow label + spaced code (mirrors the iOS invite-code field).
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", code))) }
        }) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.copy_code),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Birthday date picker field — read-only OutlinedTextField that opens a DatePickerDialog on tap.
 *  Stores/returns ISO-8601 (yyyy-MM-dd). Initialises to [value] when already set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayPickerField(
    value: String,
    onChange: (String) -> Unit,
    label: String = stringResource(R.string.birthday_optional),
) {
    var showPicker by remember { mutableStateOf(false) }
    val fallbackMillis = System.currentTimeMillis() - 30L * 365 * 24 * 60 * 60 * 1000
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                if (value.isNotEmpty()) {
                    runCatching {
                        java.time.LocalDate
                            .parse(value)
                            .atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                    }.getOrDefault(fallbackMillis)
                } else {
                    fallbackMillis
                },
        )
    // Re-sync picker to confirmed value whenever dialog re-opens (handles cancel→reopen case)
    LaunchedEffect(showPicker) {
        if (showPicker && value.isNotEmpty()) {
            pickerState.selectedDateMillis = runCatching {
                java.time.LocalDate
                    .parse(value)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: pickerState.selectedDateMillis
        }
    }

    val displayText =
        if (value.isNotEmpty()) {
            runCatching {
                java.time.LocalDate
                    .parse(value)
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("MMMM d, yyyy"),
                    )
            }.getOrDefault(value)
        } else {
            ""
        }

    // Filled tap-to-pick row — leading cake icon, value/placeholder, trailing dropdown
    // (mirrors the iOS BirthdayPickerField glass row).
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { showPicker = true }
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Cake,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = displayText.ifEmpty { label },
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (displayText.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date =
                            java.time.Instant
                                .ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                        onChange(date.toString())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * iOS-style swipe-to-delete. The row is flush by default; dragging left reveals a red delete
 * region that **grows with the finger**, snaps open past the halfway point, and **commits the
 * delete on a full swipe** (past ~55% of the row) — with a haptic tick at the full-swipe threshold.
 * Mirrors iOS `.swipeActions(role: .destructive)`.
 */
@Composable
fun SwipeToRevealDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val openPx = remember(density) { with(density) { 84.dp.toPx() } }
    var rowWidthPx by remember { mutableStateOf(1f) }
    var crossedFull by remember { mutableStateOf(false) }

    val settle: (Float) -> Unit = { velocity ->
        val fullSwipe = -offsetX.value > rowWidthPx * 0.55f
        val flung = velocity < -1200f
        scope.launch {
            when {
                fullSwipe || (flung && -offsetX.value > openPx * 0.5f) -> {
                    offsetX.animateTo(-rowWidthPx, tween(180))
                    onDelete()
                    offsetX.snapTo(0f)
                }
                -offsetX.value > openPx * 0.5f || flung ->
                    offsetX.animateTo(-openPx, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                else -> offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .onSizeChanged { rowWidthPx = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Red delete region — width tracks the drag, so it grows wider and wider like iOS.
        val revealPx = (-offsetX.value).coerceAtLeast(0f)
        val progress = (revealPx / openPx).coerceIn(0f, 1f)
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .drawBehind { drawRect(color = Danger, size = Size(revealPx, size.height), topLeft = Offset(size.width - revealPx, 0f)) },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .width(with(density) { revealPx.toDp() })
                    .fillMaxHeight()
                    .clickable {
                        scope.launch {
                            offsetX.animateTo(-rowWidthPx, tween(180))
                            onDelete()
                            offsetX.snapTo(0f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    stringResource(R.string.delete),
                    tint = Color.White,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = progress
                            scaleX = 0.6f + 0.4f * progress
                            scaleY = 0.6f + 0.4f * progress
                        },
                )
            }
        }

        // Foreground content — slides left with the drag.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            crossedFull = false
                            settle(tracker.calculateVelocity().x)
                        },
                        onDragCancel = {
                            crossedFull = false
                            settle(0f)
                        },
                        onHorizontalDrag = { change, delta ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // Rubber-band resistance past the open detent so it feels weighty.
                            val raw = offsetX.value + delta
                            val next =
                                if (raw < -openPx) -openPx + (raw + openPx) * 0.5f else raw
                            scope.launch { offsetX.snapTo(next.coerceIn(-rowWidthPx, 0f)) }
                            val past = -next > rowWidthPx * 0.55f
                            if (past && !crossedFull) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                crossedFull = true
                            } else if (!past) {
                                crossedFull = false
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Shimmer placeholder used while content is loading. */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val shimmerColors =
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer",
    )
    val brush =
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 1000f, 0f),
            end = Offset(translateAnim, 0f),
        )
    Box(modifier = modifier.clip(shape).background(brush))
}

/** Wraps a scrollable screen body with swipe-down pull-to-refresh. [onRefresh] suspends
 *  until the reload completes (e.g. `viewModel.refresh().join()`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                onRefresh()
                refreshing = false
            }
        },
        modifier = modifier,
    ) {
        content()
    }
}

/** Content-shaped loading placeholder for list screens — a column of shimmer cards.
 *  Use instead of a bare spinner so the first paint resembles the eventual content. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(rows) {
            SkeletonLoader(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(Radius.card),
            )
        }
    }
}

/** Destructive-action confirmation dialog with error-styled confirm button. */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Runs [onResume] every time the host screen's lifecycle hits ON_RESUME — i.e.
 * each time the user navigates (back) to the screen. Needed because feature
 * ViewModels are Activity-scoped (hoisted in MainFlow), so their init{} runs
 * only once; screens use this to re-fetch on every re-entry.
 */
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * iOS-style switch colours: solid muted track with a white thumb when off (no outline ring —
 * the M3 default outline reads as broken on dark glass sheets).
 */
@Composable
fun appSwitchColors() =
    androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        uncheckedBorderColor = Color.Transparent,
    )
