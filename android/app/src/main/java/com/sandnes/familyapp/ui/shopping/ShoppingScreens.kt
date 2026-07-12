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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.ShoppingItemModel
import com.sandnes.familyapp.data.ShoppingListModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.ConfirmationDialog
import com.sandnes.familyapp.ui.components.CreationSheet
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.InputDialog
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PillTag
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
import com.sandnes.familyapp.ui.theme.LiveGreen
import com.sandnes.familyapp.ui.theme.LiveGreenText
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.hexColor
import com.sandnes.familyapp.ui.theme.rowSurface

@Composable
private fun shoppingProgressLabel(p: ListProgress?): String =
    when {
        p == null || p.total == 0 -> stringResource(R.string.no_items_yet)
        p.bought == p.total -> stringResource(R.string.all_bought)
        else -> stringResource(R.string.count_of_count_bought, p.bought, p.total)
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
    var pendingDelete by remember { mutableStateOf<ShoppingListModel?>(null) }

    RefreshOnResume { viewModel.refresh() }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
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
        topBar = { FeatureTopBar(stringResource(R.string.shopping_lists), onBack) },
        floatingActionButton = {
            AppFab(text = stringResource(R.string.new_list), icon = Icons.Filled.Add, onClick = { showAdd = true })
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
                        stringResource(R.string.no_lists_yet),
                        stringResource(R.string.create_a_shared_shopping_list_for_your_family),
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
                            // Shared family data — confirm before destroying (no undo exists).
                            onDelete = { pendingDelete = list },
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

    pendingDelete?.let { list ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_list_q),
            message = stringResource(R.string.delete_list_confirm, list.title),
            onConfirm = {
                viewModel.deleteList(list)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
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
    // Follow the list only when it GROWS (new item added). Checking an item off also changes
    // active.size — scrolling then would yank the user away from their place mid-list.
    var prevActiveCount by remember { mutableStateOf(active.size) }
    LaunchedEffect(active.size) {
        if (active.size > prevActiveCount) listState.animateScrollToItem(active.size - 1)
        prevActiveCount = active.size
    }

    fun addItem() {
        val text = newItemText.trim()
        if (text.isNotBlank()) {
            viewModel.addItem(listId, text)
            newItemText = ""
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
    errorRes?.let { res ->
        val message = stringResource(res)
        LaunchedEffect(res) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    val undoItem by viewModel.undoItem.collectAsStateWithLifecycle()
    undoItem?.let { deleted ->
        val message = stringResource(R.string.item_deleted)
        val undoLabel = stringResource(R.string.undo)
        LaunchedEffect(deleted.id) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(deleted)
            viewModel.clearUndo()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeatureTopBar(list?.title ?: stringResource(R.string.list_label), onBack) {
                if (items.isNotEmpty()) {
                    PillTag(
                        stringResource(R.string.count_left, remaining),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.padding(end = Spacing.xs),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_list)) },
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
                                showChangeIcon = true
                            },
                        )
                        if (completed.isNotEmpty()) {
                            // Destructive action — red, like iOS's destructive menu role.
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.clear_completed_count, completed.size),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
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
                EmptyState(
                    Icons.Filled.ShoppingCart,
                    stringResource(R.string.empty_list),
                    stringResource(R.string.tap_the_field_below_to_add_your_first_item),
                )
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
            title = stringResource(R.string.rename_list),
            label = stringResource(R.string.list_name),
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
                .heightIn(min = 46.dp)
                .glassCard(cornerRadius = 23.dp)
                .padding(horizontal = Spacing.lg),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.add_item_field),
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
                Icons.Filled.ArrowUpward,
                contentDescription = stringResource(R.string.add_item),
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

    // Full-capsule rows, like the iOS list items.
    SwipeToRevealDelete(onDelete = { viewModel.deleteItem(item) }, modifier = modifier, shape = RoundedCornerShape(Radius.large)) {
        Row(
            Modifier
                .fillMaxWidth()
                .rowSurface(ghost = item.checked, cornerRadius = Radius.large)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
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
            stringResource(R.string.completed_count, count).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription =
                if (expanded) {
                    stringResource(R.string.hide_completed_items)
                } else {
                    stringResource(R.string.show_completed_items)
                },
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

    // iOS-parity creation sheet (NewListSheet): name field, icon grid, colour row.
    CreationSheet(
        title = stringResource(R.string.new_list),
        confirmTitle = stringResource(R.string.create),
        confirmEnabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(title.trim(), selectedIcon, selectedColor) },
    ) {
        SheetField(
            icon = IconKeyMap.shopping(selectedIcon),
            placeholder = stringResource(R.string.list_name),
            value = title,
            onValueChange = { title = it },
        )
        SheetSectionLabel(stringResource(R.string.icon))
        IconGrid(
            options = IconOptions.shopping,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Shopping,
            colorOverride = hexColor(selectedColor),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
    }
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
            options = IconOptions.shopping,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Shopping,
            colorOverride = hexColor(selectedColor),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = selectedColor, onSelect = { selectedColor = it })
    }
}
