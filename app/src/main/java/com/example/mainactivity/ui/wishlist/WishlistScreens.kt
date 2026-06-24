package com.example.mainactivity.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.ui.components.EmptyState
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.FeatureTopBar
import com.example.mainactivity.ui.components.InputDialog
import com.example.mainactivity.ui.components.SwipeToRevealDelete

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: WishlistViewModel = viewModel()
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
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New wishlist") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState()
            }
        } else if (wishlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.CardGiftcard, "No wishlists", "Create a wishlist and share gift ideas.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(wishlists, key = { it.id }) { wl ->
                    SwipeToRevealDelete(onDelete = { viewModel.deleteWishlist(wl) }, shape = RoundedCornerShape(20.dp)) {
                        Surface(
                            onClick = { onOpen(wl.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.CardGiftcard, null, tint = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(Modifier.size(12.dp))
                                Text(wl.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        InputDialog("New wishlist", "Wishlist name", onDismiss = { showAdd = false }) { v, _ ->
            viewModel.addWishlist(v); showAdd = false
        }
    }
}

@Composable
fun WishlistDetailScreen(
    wishlistId: String,
    onBack: () -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    androidx.compose.runtime.LaunchedEffect(wishlistId) { viewModel.loadWishlistDetail(wishlistId) }
    val wishlist by viewModel.selectedWishlist.collectAsStateWithLifecycle()
    val wishes by viewModel.wishes.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FeatureTopBar(wishlist?.name ?: "Wishlist", onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Filled.Add, "Add wish") }
        }
    ) { padding ->
        if (wishes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.CardGiftcard, "No wishes yet", "Add something you'd love to receive.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(wishes, key = { it.id }) { wish ->
                    SwipeToRevealDelete(onDelete = { viewModel.deleteWish(wish) }, shape = RoundedCornerShape(16.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.toggle(wish) }) {
                                    Icon(
                                        if (wish.checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        null,
                                        tint = if (wish.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    wish.text,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (wish.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (wish.checked) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        InputDialog("Add wish", "I would love…", confirmText = "Add", onDismiss = { showAdd = false }) { v, _ ->
            viewModel.addWish(wishlistId, v); showAdd = false
        }
    }
}
