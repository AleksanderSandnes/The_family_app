package com.example.mainactivity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.IconButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.mainactivity.ui.theme.BrandGradient

/** Primary gradient call-to-action button with built-in loading state. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    gradient: Brush = BrandGradient
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val active = enabled && !loading

    Box(
        modifier = modifier
            .scale(scale)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) gradient else Brush.linearGradient(listOf(Color(0xFFB6BAD6), Color(0xFFB6BAD6))))
            .clickable(interactionSource = interaction, indication = null, enabled = active) { onClick() },
        contentAlignment = Alignment.Center
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
    leadingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let {
                Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Premium text field used across auth and forms. */
@Composable
fun FamilyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        action?.invoke()
    }
}

/** Rounded gradient avatar showing the first letter of a name, or a photo when avatarUri is set. */
@Composable
fun InitialAvatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 44,
    avatarUri: String? = null
) {
    var imageFailed by remember(avatarUri) { mutableStateOf(false) }
    if (avatarUri != null && !imageFailed) {
        AsyncImage(
            model = avatarUri,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(CircleShape),
            onError = { imageFailed = true }
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Inline error banner that animates in/out. */
@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ) {
            Text(
                message ?: "",
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PillTag(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

/** Local content color helper for icon rows. */
@Composable
fun rowContentColor(): Color = LocalContentColor.current

/** Birthday date picker field — read-only OutlinedTextField that opens a DatePickerDialog on tap.
 *  Stores/returns ISO-8601 (yyyy-MM-dd). Initialises to [value] when already set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayPickerField(value: String, onChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val fallbackMillis = System.currentTimeMillis() - 30L * 365 * 24 * 60 * 60 * 1000
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (value.isNotEmpty()) {
            runCatching {
                java.time.LocalDate.parse(value)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(fallbackMillis)
        } else fallbackMillis
    )
    // Re-sync picker to confirmed value whenever dialog re-opens (handles cancel→reopen case)
    LaunchedEffect(showPicker) {
        if (showPicker && value.isNotEmpty()) {
            pickerState.selectedDateMillis = runCatching {
                java.time.LocalDate.parse(value)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: pickerState.selectedDateMillis
        }
    }

    val displayText = if (value.isNotEmpty()) {
        runCatching {
            java.time.LocalDate.parse(value)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        }.getOrDefault(value)
    } else ""

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            label = { Text("Birthday (optional)") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        onChange(date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Apple Mail-style swipe-left-to-reveal-delete. Wraps any content; swiping left past the halfway
 *  point reveals a red Delete button anchored to the trailing edge. */
@Composable
fun SwipeToRevealDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var revealed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val revealWidthDp = 80.dp
    val revealPx = remember(density) { with(density) { revealWidthDp.toPx() } }

    Box(modifier.fillMaxWidth().clipToBounds()) {
        // Red delete panel — behind the sliding content
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .width(revealWidthDp)
                    .fillMaxHeight()
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    revealed = false
                    onDelete()
                }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = Color.White)
                }
            }
        }

        // Foreground content — slides left on drag
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -(revealPx / 2)) {
                                    offsetX.animateTo(-revealPx)
                                    revealed = true
                                } else {
                                    offsetX.animateTo(0f)
                                    revealed = false
                                }
                            }
                        },
                        onHorizontalDrag = { _, delta ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
