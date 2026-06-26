@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.wishlist

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.SwipeToRevealDelete

// ─── Icon catalogue ──────────────────────────────────────────────────────────

private data class WishlistIconOption(
    val key: String,
    val vector: ImageVector,
)

private val WISHLIST_ICON_OPTIONS =
    listOf(
        WishlistIconOption("card_giftcard", Icons.Filled.CardGiftcard),
        WishlistIconOption("star", Icons.Filled.Star),
        WishlistIconOption("favorite", Icons.Filled.Favorite),
        WishlistIconOption("celebration", Icons.Filled.Celebration),
        WishlistIconOption("flight", Icons.Filled.Flight),
        WishlistIconOption("home", Icons.Filled.Home),
        WishlistIconOption("restaurant", Icons.Filled.Restaurant),
        WishlistIconOption("fitness_center", Icons.Filled.FitnessCenter),
        WishlistIconOption("shopping_cart", Icons.Filled.ShoppingCart),
        WishlistIconOption("pets", Icons.Filled.Pets),
        WishlistIconOption("local_hospital", Icons.Filled.LocalHospital),
        WishlistIconOption("cake", Icons.Filled.Cake),
    )

private fun wishlistIconVector(key: String): ImageVector =
    WISHLIST_ICON_OPTIONS.firstOrNull { it.key == key }?.vector ?: Icons.Filled.CardGiftcard

// ─── Screens ─────────────────────────────────────────────────────────────────

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val wishlists by viewModel.wishlists.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    var showAdd by remember { mutableStateOf(false) }

    RefreshOnResume { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar("Wishlists", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New wishlist") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { contentDescription = "Create new wishlist" },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    LoadingState()
                }
            }
            wishlists.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.CardGiftcard,
                        "No wishlists yet",
                        "Create a wishlist to share with your family",
                    )
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(wishlists, key = { it.id }) { wl ->
                        SwipeToRevealDelete(onDelete = { viewModel.deleteWishlist(wl) }, shape = RoundedCornerShape(20.dp)) {
                            Surface(
                                onClick = { onOpen(wl.id) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Large icon in colored circle
                                    Box(
                                        Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            wishlistIconVector(wl.icon),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            wl.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (wl.ownerName.isNotEmpty()) {
                                            Text(
                                                "By ${wl.ownerName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewWishlistDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, icon ->
                viewModel.addWishlist(name, icon)
                showAdd = false
            },
        )
    }
}

@Composable
fun WishlistDetailScreen(
    wishlistId: String,
    onBack: () -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(wishlistId) { viewModel.loadWishlistDetail(wishlistId) }
    val wishlist by viewModel.selectedWishlist.collectAsStateWithLifecycle()
    val wishes by viewModel.wishes.collectAsStateWithLifecycle()

    var newWishText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showChangeIconDialog by remember { mutableStateOf(false) }

    val sortedWishes = remember(wishes) { wishes.sortedWith(compareBy { it.checked }) }
    val listState = rememberLazyListState()
    LaunchedEffect(sortedWishes.size) {
        if (sortedWishes.isNotEmpty()) listState.animateScrollToItem(sortedWishes.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeatureTopBar(
                title = wishlist?.name ?: "Wishlist",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename wishlist") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Change icon") },
                            onClick = {
                                showMenu = false
                                showChangeIconDialog = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newWishText,
                        onValueChange = { newWishText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a wish…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = newWishText.trim()
                            if (text.isNotEmpty()) {
                                viewModel.addWish(wishlistId, text)
                                newWishText = ""
                            }
                        },
                        enabled = newWishText.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Add wish",
                            tint =
                                if (newWishText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (sortedWishes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.Redeem, "No wishes yet", "Add wishes to this list")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sortedWishes, key = { it.id }) { wish ->
                    SwipeToRevealDelete(onDelete = { viewModel.deleteWish(wish) }, shape = RoundedCornerShape(16.dp)) {
                        Surface(
                            onClick = { viewModel.toggle(wish) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = "${wish.text}, ${if (wish.checked) "claimed" else "unclaimed"}"
                                    },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { viewModel.toggle(wish) }) {
                                    Icon(
                                        if (wish.checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = if (wish.checked) "Unmark as claimed" else "Mark as claimed",
                                        tint =
                                            if (wish.checked) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                                Text(
                                    wish.text,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                        if (wish.checked) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    textDecoration = if (wish.checked) TextDecoration.LineThrough else TextDecoration.None,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameWishlistDialog(
            currentName = wishlist?.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameWishlist(wishlistId, newName)
                showRenameDialog = false
            },
        )
    }

    if (showChangeIconDialog) {
        ChangeWishlistIconDialog(
            currentIcon = wishlist?.icon ?: "card_giftcard",
            onDismiss = { showChangeIconDialog = false },
            onConfirm = { newIcon ->
                viewModel.changeWishlistIcon(wishlistId, newIcon)
                showChangeIconDialog = false
            },
        )
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun NewWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("card_giftcard") }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("New wishlist", style = MaterialTheme.typography.titleLarge) },
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
                            wishlistIconVector(selectedIcon),
                            contentDescription = "Change icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Wishlist name") },
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
                    WishlistIconPickerGrid(
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
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedIcon) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameWishlistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Rename wishlist", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Wishlist name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangeWishlistIconDialog(
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
            WishlistIconPickerGrid(selected = selectedIcon, onSelect = { selectedIcon = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIcon) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WishlistIconPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        WISHLIST_ICON_OPTIONS.chunked(4).forEach { row ->
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
                            contentDescription = null,
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
                // Fill remaining cells in the last row
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
