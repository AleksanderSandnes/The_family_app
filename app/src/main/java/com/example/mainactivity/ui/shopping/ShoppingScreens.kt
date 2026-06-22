package com.example.mainactivity.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.ShoppingItemEntity
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.PillTag

@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onOpenList: (Long) -> Unit,
    viewModel: ShoppingViewModel = viewModel()
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Shopping lists", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New list") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ShoppingCart, "No lists yet", "Create a shared shopping list for your family.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lists, key = { it.id }) { list ->
                    Surface(
                        onClick = { onOpenList(list.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).let { it },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(list.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { viewModel.deleteList(list) }) {
                                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onConfirm = { value, _ -> viewModel.addList(value); showAdd = false }
        )
    }
}

@Composable
fun ShoppingDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    viewModel: ShoppingViewModel = viewModel()
) {
    val list by viewModel.list(listId).collectAsStateWithLifecycle(null)
    val items by viewModel.items(listId).collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val remaining = items.count { !it.checked }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeatureTopBar(list?.title ?: "List", onBack) {
                if (items.isNotEmpty()) {
                    PillTag(
                        "$remaining left",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.padding(end = 12.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Filled.Add, "Add item") }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ShoppingCart, "Empty list", "Add the first item to get started.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item -> ShoppingItemRow(item, viewModel) }
            }
        }
    }

    if (showAdd) {
        InputDialog(
            title = "Add item",
            label = "Item",
            confirmText = "Add",
            onDismiss = { showAdd = false },
            onConfirm = { value, _ -> viewModel.addItem(listId, value); showAdd = false }
        )
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemEntity, viewModel: ShoppingViewModel) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.checked, onCheckedChange = { viewModel.toggle(item) })
            Text(
                item.item,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
            )
            IconButton(onClick = { viewModel.deleteItem(item) }) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
