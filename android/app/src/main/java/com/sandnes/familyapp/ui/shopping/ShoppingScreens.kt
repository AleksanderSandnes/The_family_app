@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.data.ShoppingItemModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.InputDialog
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PillTag
import com.sandnes.familyapp.ui.components.PullRefresh
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.GlassProgressBar
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.IconOptions
import com.sandnes.familyapp.ui.theme.LiveGreen
import com.sandnes.familyapp.ui.theme.LiveGreenText
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.hexColor
import com.sandnes.familyapp.ui.theme.rowSurface

private fun shoppingProgressLabel(p: ListProgress?): String =
    when {
        p == null || p.total == 0 -> "No items yet"
        p.bought == p.total -> "All bought"
        else -> "${p.bought} of ${p.total} bought"
    }

@Composable
fun ShoppingScreen(
    onOpenList: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val progress by viewModel.listProgress.collectAsStateWithLifecycle(emptyMap())
    var showAdd by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { FeatureTopBar("Shopping lists", onBack) },
        floatingActionButton = {
            AppFab(text = "New list", icon = Icons.Filled.Add, onClick = { showAdd = true })
        },
    ) { padding ->
        PullRefresh(
            onRefresh = { viewModel.refresh().join() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (isLoading) {
                ListSkeleton(Modifier.fillMaxSize())
            } else if (lists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.ShoppingCart,
                        "No lists yet",
                        "Create a shared shopping list for your family.",
                        actionLabel = "New list",
                        onAction = { showAdd = true },
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.screenEdge),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(lists, key = { it.id }) { list ->
                        SwipeToRevealDelete(
                            onDelete = { viewModel.deleteList(list) },
                            modifier = Modifier.animateItem(),
                            shape = RoundedCornerShape(Radius.overviewCard),
                        ) {
                            ShoppingListCard(
                                title = list.title,
                                iconKey = list.icon,
                                color = list.color,
                                progress = progress[list.id],
                                onClick = { onOpenList(list.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewListDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, icon, color ->
                viewModel.addList(title, icon, color)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ShoppingListCard(
    title: String,
    iconKey: String,
    color: Int?,
    progress: ListProgress?,
    onClick: () -> Unit,
) {
    val fraction =
        if (progress != null && progress.total > 0) {
            progress.bought.toFloat() / progress.total.toFloat()
        } else {
            0f
        }
    val allDone = progress != null && progress.total > 0 && progress.bought == progress.total

    Column(
        Modifier
            .fillMaxWidth()
            .rowSurface(ghost = allDone, cornerRadius = Radius.overviewCard)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (allDone) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Radius.badgeLarge))
                        .background(LiveGreen.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, tint = LiveGreen, modifier = Modifier.size(22.dp))
                }
            } else {
                FeatureBadge(
                    icon = IconKeyMap.shopping(iconKey),
                    feature = FeatureAccent.Shopping,
                    size = 44.dp,
                    cornerRadius = Radius.badgeLarge,
                    colorOverride = hexColor(color),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (allDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    shoppingProgressLabel(progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allDone) LiveGreenText else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!allDone && progress != null) {
            GlassProgressBar(value = fraction, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ShoppingDetailScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    LaunchedEffect(listId) { viewModel.loadListDetail(listId) }
    val list by viewModel.selectedList.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var newItemText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showChangeIcon by remember { mutableStateOf(false) }
    val remaining = items.count { !it.checked }
    val active = remember(items) { items.filter { !it.checked } }
    val completed = remember(items) { items.filter { it.checked } }
    var showCompleted by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LaunchedEffect(active.size) {
        if (active.isNotEmpty()) listState.animateScrollToItem(active.size - 1)
    }

    fun addItem() {
        val text = newItemText.trim()
        if (text.isNotBlank()) {
            viewModel.addItem(listId, text)
            newItemText = ""
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Transparent,
        topBar = {
            FeatureTopBar(list?.title ?: "List", onBack) {
                if (items.isNotEmpty()) {
                    PillTag(
                        "$remaining left",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.padding(end = Spacing.xs),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename list") },
                            onClick = {
                                showMenu = false
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Change icon") },
                            onClick = {
                                showMenu = false
                                showChangeIcon = true
                            },
                        )
                        if (completed.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear completed (${completed.size})") },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearCompleted(listId)
                                },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AddItemBar(
                value = newItemText,
                onValueChange = { newItemText = it },
                onSubmit = { addItem() },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ShoppingCart, "Empty list", "Tap the field below to add your first item.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(active, key = { it.id }) { item ->
                    ShoppingItemRow(item, viewModel, Modifier.animateItem())
                }
                if (completed.isNotEmpty()) {
                    item(key = "completed-header") {
                        CompletedHeader(
                            count = completed.size,
                            expanded = showCompleted,
                            onToggle = { showCompleted = !showCompleted },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (showCompleted) {
                        items(completed, key = { it.id }) { item ->
                            ShoppingItemRow(item, viewModel, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        InputDialog(
            title = "Rename list",
            label = "List name",
            initial = list?.title ?: "",
            onDismiss = { showRename = false },
            onConfirm = { value, _ ->
                viewModel.renameList(listId, value)
                showRename = false
            },
        )
    }

    if (showChangeIcon) {
        ChangeIconDialog(
            currentIcon = list?.icon ?: "shopping_cart",
            currentColor = list?.color,
            onDismiss = { showChangeIcon = false },
            onConfirm = { icon, color ->
                viewModel.changeListIcon(listId, icon)
                viewModel.changeListColor(listId, color)
                showChangeIcon = false
            },
        )
    }
}

@Composable
private fun AddItemBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(46.dp)
                .glassCard(cornerRadius = 23.dp)
                .padding(horizontal = Spacing.lg),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    "Add item…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        val enabled = value.isNotBlank()
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Add item",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemModel,
    viewModel: ShoppingViewModel,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(item.item) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    fun commitEdit() {
        if (!isEditing) return
        val trimmed = editText.trim()
        isEditing = false
        if (trimmed.isNotBlank() && trimmed != item.item) {
            viewModel.renameItem(item, trimmed)
        } else {
            editText = item.item
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    SwipeToRevealDelete(onDelete = { viewModel.deleteItem(item) }, modifier = modifier, shape = RoundedCornerShape(Radius.row)) {
        Row(
            Modifier
                .fillMaxWidth()
                .rowSurface(ghost = item.checked, cornerRadius = Radius.row)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.toggle(item)
            }) {
                Icon(
                    if (item.checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    null,
                    tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (!it.isFocused) commitEdit() },
                )
            } else {
                Text(
                    item.item,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable { isEditing = true },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                )
            }
        }
    }
}

@Composable
private fun CompletedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.badge))
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Completed ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Hide completed items" else "Show completed items",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewListDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, icon: String, color: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("shopping_cart") }
    var selectedColor by remember { mutableStateOf(AppColorPalette.first()) }

    IconColorDialog(
        heading = "New shopping list",
        confirmLabel = "Create",
        title = title,
        onTitleChange = { title = it },
        titlePlaceholder = "List name",
        selectedIcon = selectedIcon,
        onIconSelect = { selectedIcon = it },
        selectedColor = selectedColor,
        onColorSelect = { selectedColor = it },
        confirmEnabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(title.trim(), selectedIcon, selectedColor) },
    )
}

@Composable
private fun ChangeIconDialog(
    currentIcon: String,
    currentColor: Int?,
    onDismiss: () -> Unit,
    onConfirm: (icon: String, color: Int) -> Unit,
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }
    var selectedColor by remember { mutableStateOf(currentColor ?: AppColorPalette.first()) }

    IconColorDialog(
        heading = "Change icon",
        confirmLabel = "Save",
        title = null,
        onTitleChange = {},
        titlePlaceholder = "",
        selectedIcon = selectedIcon,
        onIconSelect = { selectedIcon = it },
        selectedColor = selectedColor,
        onColorSelect = { selectedColor = it },
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selectedIcon, selectedColor) },
    )
}

/**
 * Shared new/edit dialog body for shopping lists: optional name field, icon grid and colour row.
 * Passing a null [title] hides the name field (used by the change-icon path).
 */
@Composable
private fun IconColorDialog(
    heading: String,
    confirmLabel: String,
    title: String?,
    onTitleChange: (String) -> Unit,
    titlePlaceholder: String,
    selectedIcon: String,
    onIconSelect: (String) -> Unit,
    selectedColor: Int,
    onColorSelect: (Int) -> Unit,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (title != null) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        placeholder = { Text(titlePlaceholder) },
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
                }
                Text(
                    "ICON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconGrid(
                    options = IconOptions.shopping,
                    selected = selectedIcon,
                    onSelect = onIconSelect,
                    feature = FeatureAccent.Shopping,
                    colorOverride = hexColor(selectedColor),
                )
                Text(
                    "COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerRow(selected = selectedColor, onSelect = onColorSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
