@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.meal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.SwipeToRevealDelete
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Icon infrastructure ────────────────────────────────────────────────────

private data class MealIconOption(
    val key: String,
    val vector: ImageVector,
)

private val MEAL_ICON_OPTIONS =
    listOf(
        MealIconOption("restaurant", Icons.Filled.Restaurant),
        MealIconOption("restaurant_menu", Icons.Filled.RestaurantMenu),
        MealIconOption("lunch_dining", Icons.Filled.LunchDining),
        MealIconOption("dinner_dining", Icons.Filled.DinnerDining),
        MealIconOption("bakery_dining", Icons.Filled.BakeryDining),
        MealIconOption("local_pizza", Icons.Filled.LocalPizza),
        MealIconOption("ramen_dining", Icons.Filled.RamenDining),
        MealIconOption("set_meal", Icons.Filled.SetMeal),
        MealIconOption("fastfood", Icons.Filled.Fastfood),
        MealIconOption("cake", Icons.Filled.Cake),
        MealIconOption("local_cafe", Icons.Filled.LocalCafe),
        MealIconOption("outdoor_grill", Icons.Filled.OutdoorGrill),
        MealIconOption("kitchen", Icons.Filled.Kitchen),
        MealIconOption("egg", Icons.Filled.Egg),
        MealIconOption("local_bar", Icons.Filled.LocalBar),
    )

private fun mealIconVector(key: String): ImageVector =
    MEAL_ICON_OPTIONS.find { it.key == key }?.vector ?: Icons.Filled.Restaurant

// ── Date formatting ────────────────────────────────────────────────────────

private val MEAL_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

private fun formatMealDate(stored: String): String =
    runCatching { LocalDate.parse(stored).format(MEAL_DATE_FMT) }.getOrDefault(stored)

// ── Shared icon picker grid ────────────────────────────────────────────────

@Composable
private fun MealIconPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        MEAL_ICON_OPTIONS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { opt ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected == opt.key) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ).clickable { onSelect(opt.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            opt.vector,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint =
                                if (selected == opt.key) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Create plan dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlanDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, fromIso: String, toIso: String, icon: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedIcon by remember { mutableStateOf("restaurant") }
    var showIconPicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val canConfirm =
        name.isNotBlank() &&
            fromDate != null &&
            toDate != null &&
            !toDate!!.isBefore(fromDate!!)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Create a meal plan", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon toggle + name field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { showIconPicker = !showIconPicker },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            mealIconVector(selectedIcon),
                            "Change icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Plan name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
                AnimatedVisibility(visible = showIconPicker) {
                    MealIconPickerGrid(selected = selectedIcon) {
                        selectedIcon = it
                        showIconPicker = false
                    }
                }
                // Date range buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                onClick = { onCreate(name.trim(), fromDate.toString(), toDate.toString(), selectedIcon) },
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
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        )
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
    var showCreate by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Meal planner", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Create a meal plan") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { contentDescription = "Create new meal plan" },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (plans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Filled.Restaurant,
                    "No meal plans yet",
                    "No meal plans yet. Start planning!",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    val dayCount =
                        runCatching {
                            val from = LocalDate.parse(plan.fromDate)
                            val to = LocalDate.parse(plan.toDate)
                            (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(0)
                        }.getOrDefault(0)
                    val dateRange = "${formatMealDate(plan.fromDate)} – ${formatMealDate(plan.toDate)}"
                    val planName = plan.name.ifBlank { "Meal plan" }
                    val cardDescription = "$planName, $dateRange, $dayCount days"

                    SwipeToRevealDelete(
                        onDelete = { viewModel.deletePlan(plan) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Card(
                            onClick = { onOpen(plan.id) },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = cardDescription },
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        mealIconVector(plan.icon),
                                        null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        planName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        dateRange,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "$dayCount days planned",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlanDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, from, to, icon ->
                viewModel.createPlan(name, from, to, icon)
                showCreate = false
            },
        )
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
        containerColor = MaterialTheme.colorScheme.background,
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
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Plan header below the top bar
            plan?.let { p ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    mealIconVector(p.icon),
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Text(
                                    p.name.ifBlank { "Meal plan" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "${formatMealDate(p.fromDate)} – ${formatMealDate(p.toDate)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            items(days, key = { it.id }) { day ->
                val isEditing = editingDayId == day.id
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(isEditing) {
                    if (isEditing) focusRequester.requestFocus()
                }

                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isEditing) {
                                editingDayId = day.id
                                draft = day.food
                            },
                ) {
                    Column {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Day name + date on the left
                            Column(Modifier.weight(1f)) {
                                Text(
                                    day.day,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    formatMealDate(day.date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Meal entry or edit field
                            if (isEditing) {
                                Column(Modifier.weight(2f)) {
                                    OutlinedTextField(
                                        value = draft,
                                        onValueChange = { draft = it },
                                        placeholder = { Text("What's for ${day.day}?") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions =
                                            KeyboardActions(onDone = {
                                                viewModel.setFood(day, draft)
                                                editingDayId = null
                                                focusManager.clearFocus()
                                            }),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .focusRequester(focusRequester),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                        ),
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        TextButton(onClick = {
                                            editingDayId = null
                                            focusManager.clearFocus()
                                        }) { Text("Cancel") }
                                        TextButton(onClick = {
                                            viewModel.setFood(day, draft)
                                            editingDayId = null
                                            focusManager.clearFocus()
                                        }) { Text("Save") }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (day.food.isBlank()) "Tap to add meal" else day.food,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                        if (day.food.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    modifier = Modifier.weight(2f),
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        editingDayId = day.id
                                        draft = day.food
                                    },
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .semantics {
                                                contentDescription = "Edit ${day.day} meal"
                                            },
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        // Subtle divider between days
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .size(height = 1.dp, width = 0.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        )
                    }
                }
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
            AlertDialog(
                onDismissRequest = { showIconPicker = false },
                shape = RoundedCornerShape(24.dp),
                title = { Text("Change icon", style = MaterialTheme.typography.titleLarge) },
                text = {
                    MealIconPickerGrid(selected = p.icon) { newIcon ->
                        viewModel.setPlanIcon(p, newIcon)
                        showIconPicker = false
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIconPicker = false }) { Text("Done") }
                },
            )
        }
    }
}
