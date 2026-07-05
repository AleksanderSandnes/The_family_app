@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.shopping

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mainactivity.data.ShoppingItemModel
import com.example.mainactivity.ui.components.AppFab
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.ListCard
import com.example.mainactivity.ui.components.ListSkeleton
import com.example.mainactivity.ui.components.PillTag
import com.example.mainactivity.ui.components.PullRefresh
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.SwipeToRevealDelete

private data class ShoppingIconOption(
    val key: String,
    val vector: ImageVector,
)

private val SHOPPING_ICON_OPTIONS =
    listOf(
        ShoppingIconOption("shopping_cart", Icons.Filled.ShoppingCart),
        ShoppingIconOption("restaurant", Icons.Filled.Restaurant),
        ShoppingIconOption("cake", Icons.Filled.Cake),
        ShoppingIconOption("local_hospital", Icons.Filled.LocalHospital),
        ShoppingIconOption("celebration", Icons.Filled.Celebration),
        ShoppingIconOption("favorite", Icons.Filled.Favorite),
        ShoppingIconOption("star", Icons.Filled.Star),
        ShoppingIconOption("fitness_center", Icons.Filled.FitnessCenter),
        ShoppingIconOption("home", Icons.Filled.Home),
        ShoppingIconOption("pets", Icons.Filled.Pets),
        ShoppingIconOption("flight", Icons.Filled.Flight),
        ShoppingIconOption("people", Icons.Filled.People),
    )

private fun shoppingIconVector(key: String): ImageVector =
    SHOPPING_ICON_OPTIONS.firstOrNull { it.key == key }?.vector ?: Icons.Filled.ShoppingCart

private fun shoppingProgressLabel(p: ListProgress?): String =
    when {
        p == null || p.total == 0 -> "No items yet"
        p.bought == p.total -> "All bought"
        else -> "${p.bought} of ${p.total} bought"
    }

@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val progress by viewModel.listProgress.collectAsStateWithLifecycle(emptyMap())
    var showAdd by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lists, key = { it.id }) { list ->
                        SwipeToRevealDelete(
                            onDelete = { viewModel.deleteList(list) },
                            modifier = Modifier.animateItem(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            ListCard(onClick = { onOpenList(list.id) }) {
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Icon(shoppingIconVector(list.icon), null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(list.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        shoppingProgressLabel(progress[list.id]),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewListDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, icon ->
                viewModel.addList(title, icon)
                showAdd = false
            },
        )
    }
}

@Composable
fun ShoppingDetailScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(listId) { viewModel.loadListDetail(listId) }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeatureTopBar(list?.title ?: "List", onBack) {
                if (items.isNotEmpty()) {
                    PillTag(
                        "$remaining left",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.padding(end = 4.dp),
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
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("Add item…") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addItem() }),
                        modifier = Modifier.weight(1f),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { addItem() },
                        enabled = newItemText.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Add item",
                            tint = if (newItemText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
            onDismiss = { showChangeIcon = false },
            onConfirm = { icon ->
                viewModel.changeListIcon(listId, icon)
                showChangeIcon = false
            },
        )
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

    SwipeToRevealDelete(onDelete = { viewModel.deleteItem(item) }, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
    onConfirm: (title: String, icon: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("shopping_cart") }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("New shopping list", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            shoppingIconVector(selectedIcon),
                            "Change icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("List name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                    )
                }
                AnimatedVisibility(visible = showIconPicker) {
                    ShoppingIconPickerGrid(
                        selected = selectedIcon,
                        onSelect = {
                            selectedIcon = it
                            showIconPicker = false
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), selectedIcon) },
                enabled = title.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangeIconDialog(
    currentIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Change icon", style = MaterialTheme.typography.titleLarge) },
        text = {
            ShoppingIconPickerGrid(selected = selectedIcon, onSelect = { selectedIcon = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIcon) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ShoppingIconPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        SHOPPING_ICON_OPTIONS.chunked(4).forEach { row ->
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
