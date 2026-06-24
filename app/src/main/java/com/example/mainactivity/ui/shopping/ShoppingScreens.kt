@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.ShoppingItemModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.PillTag
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.SwipeToRevealDelete

@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: ShoppingViewModel = viewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    var showAdd by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Shopping lists", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New list") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ShoppingCart, "No lists yet", "Create a shared shopping list for your family.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    SwipeToRevealDelete(onDelete = { viewModel.deleteList(list) }, shape = RoundedCornerShape(20.dp)) {
                        Surface(
                            onClick = { onOpenList(list.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(list.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        InputDialog(
            title = "New shopping list",
            label = "List name",
            onDismiss = { showAdd = false },
            onConfirm = { value, _ ->
                viewModel.addList(value)
                showAdd = false
            },
        )
    }
}

@Composable
fun ShoppingDetailScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: ShoppingViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(listId) { viewModel.loadListDetail(listId) }
    val list by viewModel.selectedList.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var newItemText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val remaining = items.count { !it.checked }

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
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item -> ShoppingItemRow(item, viewModel) }
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
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemModel,
    viewModel: ShoppingViewModel,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(item.item) }
    val focusRequester = remember { FocusRequester() }

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

    SwipeToRevealDelete(onDelete = { viewModel.deleteItem(item) }, shape = RoundedCornerShape(16.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggle(item) }) {
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
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (!it.isFocused) commitEdit() },
                    )
                } else {
                    Text(
                        item.item,
                        modifier = Modifier
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
