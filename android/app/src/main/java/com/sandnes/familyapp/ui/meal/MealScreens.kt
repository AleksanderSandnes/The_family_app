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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.MealPlanDayModel
import com.sandnes.familyapp.data.MealPlanModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.ConfirmationDialog
import com.sandnes.familyapp.ui.components.CreationSheet
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.InputDialog
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PullRefresh
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SheetField
import com.sandnes.familyapp.ui.components.SheetSectionLabel
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

private val MEAL_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

private fun formatMealDate(stored: String): String =
    runCatching { LocalDate.parse(stored).format(MEAL_DATE_FMT) }.getOrDefault(stored)

/** Plan card sub-label: "N of M dinners planned" once days exist, else the day count. */
@Composable
private fun mealPlanLabel(
    progress: MealProgress?,
    fromIso: String,
    toIso: String,
): String {
    if (progress != null && progress.total > 0) {
        return stringResource(R.string.dinners_planned_count, progress.planned, progress.total)
    }
    val days =
        runCatching {
            val from = LocalDate.parse(fromIso)
            val to = LocalDate.parse(toIso)
            (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    return stringResource(R.string.days_count, days)
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

    // iOS-parity creation sheet (New plan): name field, icon grid, colour row, Starts/Ends.
    CreationSheet(
        title = stringResource(R.string.new_plan),
        confirmTitle = stringResource(R.string.create),
        confirmEnabled = canConfirm,
        onDismiss = onDismiss,
        onConfirm = { onCreate(name.trim(), fromDate.toString(), toDate.toString(), selectedIcon, selectedColor) },
    ) {
        SheetField(
            icon = IconKeyMap.meal(selectedIcon),
            placeholder = stringResource(R.string.plan_name),
            value = name,
            onValueChange = { name = it },
        )
        SheetSectionLabel(stringResource(R.string.icon))
        IconGrid(
            options = IconOptions.meal,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Meals,
            colorOverride = hexColor(selectedColor),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DatePickerButton(
                label = stringResource(R.string.starts),
                value = fromDate?.format(MEAL_DATE_FMT) ?: stringResource(R.string.select),
                valueSet = fromDate != null,
                modifier = Modifier.weight(1f),
                onClick = { showFromPicker = true },
            )
            DatePickerButton(
                label = stringResource(R.string.ends),
                value = toDate?.format(MEAL_DATE_FMT) ?: stringResource(R.string.select),
                valueSet = toDate != null,
                modifier = Modifier.weight(1f),
                onClick = { showToPicker = true },
            )
        }
    }

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
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text(stringResource(R.string.cancel)) } },
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
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/**
 * Two-line tinted date button matching iOS `PlanDatePicker`: a leading calendar icon plus an
 * uppercase caption ([label], e.g. "STARTS"/"ENDS") over the [value] line (a formatted date or the
 * "Select" placeholder). [valueSet] drives the value colour (onSurface when set, muted when unset).
 */
@Suppress("MagicNumber")
@Composable
private fun DatePickerButton(
    label: String,
    value: String,
    valueSet: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.field),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .heightIn(min = 58.dp)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (valueSet) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
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

    // iOS-parity icon picker sheet (IconPickerSheet).
    CreationSheet(
        title = stringResource(R.string.change_icon),
        confirmTitle = stringResource(R.string.save),
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selectedIcon, selectedColor) },
    ) {
        SheetSectionLabel(stringResource(R.string.icon))
        IconGrid(
            options = IconOptions.meal,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Meals,
            colorOverride = hexColor(selectedColor),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
    }
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
    val hasFamily by viewModel.hasFamily.collectAsStateWithLifecycle(false)
    val errorRes by viewModel.errorRes.collectAsStateWithLifecycle(null)
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MealPlanModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    RefreshOnResume { viewModel.refresh() }

    errorRes?.let { res ->
        val message = stringResource(res)
        LaunchedEffect(res) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { FeatureTopBar(stringResource(R.string.meal_planner), onBack) },
        floatingActionButton = {
            if (hasFamily) {
                AppFab(
                    text = stringResource(R.string.create_a_meal_plan),
                    icon = Icons.Filled.Add,
                    onClick = { showCreate = true },
                )
            }
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
                        stringResource(R.string.no_meal_plans_yet),
                        stringResource(R.string.plan_your_family_s_meals_for_the_week_ahead),
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
                            // Shared family data — confirm before destroying (no undo exists).
                            onDelete = { pendingDelete = plan },
                            modifier = Modifier.animateItem(),
                            shape = RoundedCornerShape(Radius.overviewCard),
                        ) {
                            MealPlanCard(
                                name = plan.name.ifBlank { stringResource(R.string.meal_plan) },
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

    pendingDelete?.let { plan ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_plan_q),
            message = stringResource(R.string.delete_plan_confirm, plan.name.ifBlank { stringResource(R.string.meal_plan) }),
            onConfirm = {
                viewModel.deletePlan(plan)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
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
            val defaultTitle = stringResource(R.string.meal_plan)
            FeatureTopBar(plan?.name?.ifBlank { defaultTitle } ?: defaultTitle, onBack) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_plan)) },
                            trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_icon)) },
                            trailingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
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
                title = stringResource(R.string.rename_plan),
                label = stringResource(R.string.name),
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
    // Local TextFieldValue so entering edit mode places the cursor at the end (like iOS).
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(isEditing) {
        if (isEditing) {
            fieldValue = TextFieldValue(draft, TextRange(draft.length))
            focusRequester.requestFocus()
        }
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
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        onDraftChange(it.text)
                    },
                    placeholder = { Text(stringResource(R.string.whats_for_meal, day.day)) },
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
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
                }
            }
        } else {
            Text(
                text = if (day.food.isBlank()) stringResource(R.string.no_plan_yet) else day.food,
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
            val editMealDesc = stringResource(R.string.edit_meal_named, day.day)
            IconButton(
                onClick = onStartEdit,
                modifier =
                    Modifier.size(48.dp).semantics {
                        contentDescription = editMealDesc
                    },
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
