@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.meal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.InputDialog
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PullRefresh
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.GlassProgressBar
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.IconOptions
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.hexColor
import com.sandnes.familyapp.ui.theme.rowSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Date formatting ────────────────────────────────────────────────────────

private val MEAL_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

private fun formatMealDate(stored: String): String =
    runCatching { LocalDate.parse(stored).format(MEAL_DATE_FMT) }.getOrDefault(stored)

/** Plan card sub-label: "N of M dinners planned" once days exist, else the day count. */
private fun mealPlanLabel(
    progress: MealProgress?,
    fromIso: String,
    toIso: String,
): String {
    if (progress != null && progress.total > 0) {
        return "${progress.planned} of ${progress.total} dinners planned"
    }
    val days =
        runCatching {
            val from = LocalDate.parse(fromIso)
            val to = LocalDate.parse(toIso)
            (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    return "$days days"
}

// ── Create / edit dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlanDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, fromIso: String, toIso: String, icon: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedIcon by remember { mutableStateOf("restaurant") }
    var selectedColor by remember { mutableStateOf(AppColorPalette.first()) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val canConfirm =
        name.isNotBlank() &&
            fromDate != null &&
            toDate != null &&
            !toDate!!.isBefore(fromDate!!)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        title = { Text("Create a meal plan", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Plan name") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                )
                Text(
                    "ICON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconGrid(
                    options = IconOptions.meal,
                    selected = selectedIcon,
                    onSelect = { selectedIcon = it },
                    feature = FeatureAccent.Meals,
                    colorOverride = hexColor(selectedColor),
                )
                Text(
                    "COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    DatePickerButton(
                        label = fromDate?.format(MEAL_DATE_FMT) ?: "Start date",
                        modifier = Modifier.weight(1f),
                        onClick = { showFromPicker = true },
                    )
                    DatePickerButton(
                        label = toDate?.format(MEAL_DATE_FMT) ?: "End date",
                        modifier = Modifier.weight(1f),
                        onClick = { showToPicker = true },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onCreate(name.trim(), fromDate.toString(), toDate.toString(), selectedIcon, selectedColor) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showFromPicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (fromDate ?: LocalDate.now())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        fromDate = picked
                        if (toDate != null && toDate!!.isBefore(picked)) toDate = picked
                    }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (toDate ?: fromDate ?: LocalDate.now())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        toDate = if (fromDate != null && picked.isBefore(fromDate!!)) fromDate else picked
                    }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DatePickerButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.field),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
    }
}

/** Icon + colour picker dialog for the detail change-icon path. */
@Composable
private fun ChangeIconColorDialog(
    currentIcon: String,
    currentColor: Int?,
    onDismiss: () -> Unit,
    onConfirm: (icon: String, color: Int) -> Unit,
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }
    var selectedColor by remember { mutableStateOf(currentColor ?: AppColorPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        title = { Text("Change icon", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "ICON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconGrid(
                    options = IconOptions.meal,
                    selected = selectedIcon,
                    onSelect = { selectedIcon = it },
                    feature = FeatureAccent.Meals,
                    colorOverride = hexColor(selectedColor),
                )
                Text(
                    "COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIcon, selectedColor) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Screens ────────────────────────────────────────────────────────────────

@Composable
fun MealScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: MealViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val planProgress by viewModel.planProgress.collectAsStateWithLifecycle(emptyMap())
    var showCreate by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopBar("Meal planner", onBack) },
        floatingActionButton = {
            AppFab(text = "Create a meal plan", icon = Icons.Filled.Add, onClick = { showCreate = true })
        },
    ) { padding ->
        PullRefresh(
            onRefresh = { viewModel.refresh().join() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (isLoading) {
                ListSkeleton(Modifier.fillMaxSize())
            } else if (plans.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.Restaurant,
                        "No meal plans yet",
                        "Plan your family's meals for the week ahead.",
                        actionLabel = "Create a meal plan",
                        onAction = { showCreate = true },
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.screenEdge),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(plans, key = { it.id }) { plan ->
                        SwipeToRevealDelete(
                            onDelete = { viewModel.deletePlan(plan) },
                            modifier = Modifier.animateItem(),
                            shape = RoundedCornerShape(Radius.overviewCard),
                        ) {
                            MealPlanCard(
                                name = plan.name.ifBlank { "Meal plan" },
                                iconKey = plan.icon,
                                color = plan.color,
                                dateRange = "${formatMealDate(plan.fromDate)} – ${formatMealDate(plan.toDate)}",
                                label = mealPlanLabel(planProgress[plan.id], plan.fromDate, plan.toDate),
                                progress = planProgress[plan.id],
                                onClick = { onOpen(plan.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlanDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, from, to, icon, color ->
                viewModel.createPlan(name, from, to, icon, color)
                showCreate = false
            },
        )
    }
}

@Composable
private fun MealPlanCard(
    name: String,
    iconKey: String,
    color: Int?,
    dateRange: String,
    label: String,
    progress: MealProgress?,
    onClick: () -> Unit,
) {
    val fraction =
        if (progress != null && progress.total > 0) {
            progress.planned.toFloat() / progress.total.toFloat()
        } else {
            0f
        }
    val hasPlanned = (progress?.planned ?: 0) > 0

    Column(
        Modifier
            .fillMaxWidth()
            // Meal plans are always shown active — never dashed/greyed.
            .rowSurface(ghost = false, cornerRadius = Radius.overviewCard)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding)
            .semantics { contentDescription = "$name, $dateRange, $label" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureBadge(
                icon = IconKeyMap.meal(iconKey),
                feature = FeatureAccent.Meals,
                size = 44.dp,
                cornerRadius = Radius.badgeLarge,
                colorOverride = hexColor(color),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$dateRange · $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasPlanned) {
            GlassProgressBar(value = fraction, tint = FeatureAccent.Meals.stroke())
        }
    }
}

@Composable
fun MealDetailScreen(
    planId: String,
    onBack: () -> Unit,
    viewModel: MealViewModel = hiltViewModel(),
) {
    LaunchedEffect(planId) { viewModel.loadPlanDetail(planId) }
    val plan by viewModel.selectedPlan.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()

    var editingDayId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.imePadding(),
        topBar = {
            FeatureTopBar(plan?.name?.ifBlank { "Meal plan" } ?: "Meal plan", onBack) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Change icon") },
                            onClick = {
                                showMenu = false
                                showIconPicker = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(days, key = { it.id }) { day ->
                MealDayRow(
                    day = day,
                    isEditing = editingDayId == day.id,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onStartEdit = {
                        editingDayId = day.id
                        draft = day.food
                    },
                    onSave = {
                        viewModel.setFood(day, draft)
                        editingDayId = null
                        focusManager.clearFocus()
                    },
                    onCancel = {
                        editingDayId = null
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    if (showRename) {
        plan?.let { p ->
            InputDialog(
                title = "Rename plan",
                label = "Name",
                initial = p.name,
                onDismiss = { showRename = false },
                onConfirm = { v, _ ->
                    viewModel.renamePlan(p, v)
                    showRename = false
                },
            )
        }
    }

    if (showIconPicker) {
        plan?.let { p ->
            ChangeIconColorDialog(
                currentIcon = p.icon,
                currentColor = p.color,
                onDismiss = { showIconPicker = false },
                onConfirm = { icon, color ->
                    viewModel.setPlanIcon(p, icon)
                    viewModel.setPlanColor(p, color)
                    showIconPicker = false
                },
            )
        }
    }
}

@Composable
private fun MealDayRow(
    day: MealPlanDayModel,
    isEditing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = day.food.isBlank() && !isEditing
    val isToday = runCatching { LocalDate.parse(day.date) == LocalDate.now() }.getOrDefault(false)
    val highlighted = isEditing || isToday
    val weekday = day.day.take(3).uppercase()
    val dayNumber =
        runCatching { LocalDate.parse(day.date).dayOfMonth.toString() }
            .getOrDefault(day.date.substringAfterLast('-'))

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Row(
        modifier
            .fillMaxWidth()
            .rowSurface(ghost = empty, cornerRadius = Radius.row)
            .then(
                if (isEditing) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        RoundedCornerShape(Radius.row),
                    )
                } else {
                    Modifier
                },
            ).clickable(enabled = !isEditing, onClick = onStartEdit)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = if (isEditing) Alignment.Top else Alignment.CenterVertically,
    ) {
        DateBadge(weekday = weekday, dayNumber = dayNumber, highlighted = highlighted, empty = empty)
        Spacer(Modifier.width(Spacing.md))
        if (isEditing) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("What's for ${day.day}?") },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.extraSmall),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    TextButton(onClick = onSave) { Text("Save") }
                }
            }
        } else {
            Text(
                text = if (day.food.isBlank()) "No plan yet" else day.food,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (day.food.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            IconButton(
                onClick = onStartEdit,
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Edit ${day.day} meal" },
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DateBadge(
    weekday: String,
    dayNumber: String,
    highlighted: Boolean,
    empty: Boolean,
) {
    Column(
        Modifier.width(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            weekday,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (highlighted) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(dayNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    dayNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
