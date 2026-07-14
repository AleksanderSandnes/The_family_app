@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.wishlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.WishModel
import com.sandnes.familyapp.data.WishReservationModel
import com.sandnes.familyapp.data.WishlistModel
import com.sandnes.familyapp.ui.components.AppFab
import com.sandnes.familyapp.ui.components.ColorPickerRow
import com.sandnes.familyapp.ui.components.ConfirmationDialog
import com.sandnes.familyapp.ui.components.CreationSheet
import com.sandnes.familyapp.ui.components.EmptyState
import com.sandnes.familyapp.ui.components.FeatureTopBar
import com.sandnes.familyapp.ui.components.IconGrid
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.PrimaryButton
import com.sandnes.familyapp.ui.components.PullRefresh
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SectionHeader
import com.sandnes.familyapp.ui.components.SheetField
import com.sandnes.familyapp.ui.components.SheetSectionLabel
import com.sandnes.familyapp.ui.components.SwipeToRevealDelete
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.IconOptions
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.ghostSurface
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.hexColor
import com.sandnes.familyapp.ui.theme.rowSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Screens ─────────────────────────────────────────────────────────────────

@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val wishlists by viewModel.wishlists.collectAsStateWithLifecycle(emptyList())
    val sharedWishlists by viewModel.sharedWishlists.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(false)
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WishlistModel?>(null) }

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
        topBar = { FeatureTopBar(stringResource(R.string.wishlists), onBack) },
        floatingActionButton = {
            AppFab(text = stringResource(R.string.new_wishlist), icon = Icons.Filled.Add, onClick = { showAdd = true })
        },
    ) { padding ->
        PullRefresh(
            onRefresh = { viewModel.refresh().join() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                isLoading -> ListSkeleton(Modifier.fillMaxSize())
                wishlists.isEmpty() && sharedWishlists.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            Icons.Filled.CardGiftcard,
                            stringResource(R.string.no_wishlists_yet),
                            stringResource(R.string.create_a_wishlist_to_share_with_your_family),
                            actionLabel = stringResource(R.string.new_wishlist),
                            onAction = { showAdd = true },
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(wishlists, key = { it.id }) { wl ->
                            // Only the owner may delete; RLS enforces it server-side too.
                            if (wl.ownerUserId == currentUserId) {
                                SwipeToRevealDelete(
                                    // Shared family data — confirm before destroying (no undo exists).
                                    onDelete = { pendingDelete = wl },
                                    modifier = Modifier.animateItem(),
                                    shape = RoundedCornerShape(Radius.overviewCard),
                                ) {
                                    WishlistRow(wl) { onOpen(wl.id) }
                                }
                            } else {
                                WishlistRow(wl, Modifier.animateItem()) { onOpen(wl.id) }
                            }
                        }
                        if (sharedWishlists.isNotEmpty()) {
                            item(key = "shared-header") {
                                SectionHeader(stringResource(R.string.shared_with_me), Modifier.padding(top = 8.dp))
                            }
                            items(sharedWishlists, key = { "shared-${it.id}" }) { wl ->
                                WishlistRow(wl, Modifier.animateItem()) { onOpen(wl.id) }
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
            onConfirm = { name, icon, color ->
                viewModel.addWishlist(name, icon, color)
                showAdd = false
            },
        )
    }

    pendingDelete?.let { wl ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_wishlist_q),
            message = stringResource(R.string.delete_wishlist_confirm, wl.name),
            onConfirm = {
                viewModel.deleteWishlist(wl)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** A wishlist card: coloured feature badge, name, "By {owner}", chevron. */
@Composable
private fun WishlistRow(
    wl: WishlistModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.overviewCard))
            .glassCard(Radius.overviewCard)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureBadge(
            icon = IconKeyMap.wishlist(wl.icon),
            feature = FeatureAccent.Wishlists,
            size = 46.dp,
            cornerRadius = 23.dp,
            colorOverride = hexColor(wl.color),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                wl.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (wl.ownerName.isNotEmpty()) {
                Text(
                    stringResource(R.string.by_owner, wl.ownerName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var showAddWish by remember { mutableStateOf(false) }
    var wishToEdit by remember { mutableStateOf<WishModel?>(null) }
    var detailWish by remember { mutableStateOf<WishModel?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }

    val isOwner = currentUserId != null && wishlist?.ownerUserId == currentUserId
    val ownerName = wishlist?.ownerName.orEmpty()
    // Claimed wishes sink to the bottom.
    val sortedWishes = remember(wishes) { wishes.sortedWith(compareBy { it.checked }) }

    val snackbarHostState = remember { SnackbarHostState() }
    val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
    errorRes?.let { res ->
        val message = stringResource(res)
        LaunchedEffect(res) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    val undoWish by viewModel.undoWish.collectAsStateWithLifecycle()
    undoWish?.let { deleted ->
        val message = stringResource(R.string.wish_deleted)
        val undoLabel = stringResource(R.string.undo)
        LaunchedEffect(deleted.id) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreWish(deleted)
            viewModel.clearUndo()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FeatureTopBar(
                title = wishlist?.name ?: stringResource(R.string.wishlist),
                onBack = onBack,
                actions = {
                    // Every menu action is owner-only, so non-owners get no ⋯ at all.
                    if (isOwner) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isOwner) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_pdf)) },
                                trailingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    scope.launch { exportWishlistPdf(context, wishlist, sortedWishes) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_link)) },
                                trailingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    scope.launch { shareWishlistLink(context, viewModel, wishlistId, wishlist?.name) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_wishlist)) },
                                trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.change_icon)) },
                                trailingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showAppearanceDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Only the wishlist owner adds wishes; family members view + reserve.
            if (isOwner) {
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = stringResource(R.string.add_a_wish),
                        onClick = { showAddWish = true },
                        leadingIcon = Icons.Filled.Add,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Reassure family members that their claims stay secret from the owner.
            if (!isOwner && ownerName.isNotEmpty()) {
                Text(
                    stringResource(R.string.reservations_are_hidden_from, ownerName) + " 🤫",
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (sortedWishes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Filled.Redeem, stringResource(R.string.no_wishes_yet), stringResource(R.string.add_wishes_to_this_list))
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sortedWishes, key = { it.id }) { wish ->
                        if (!isOwner) {
                            MemberWishCard(
                                wish = wish,
                                reservation = reservations[wish.id],
                                currentUserId = currentUserId,
                                onReserve = { viewModel.reserve(wish) },
                                onUnreserve = { viewModel.unreserve(wish) },
                                onShowDetail = { detailWish = wish },
                                modifier = Modifier.animateItem(),
                            )
                        } else {
                            SwipeToRevealDelete(
                                onDelete = { viewModel.deleteWish(wish) },
                                modifier = Modifier.animateItem(),
                                shape = RoundedCornerShape(Radius.row),
                            ) {
                                OwnerWishRow(
                                    wish = wish,
                                    onToggle = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggle(wish)
                                    },
                                    onEdit = { wishToEdit = wish },
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

    if (showAppearanceDialog) {
        WishlistAppearanceDialog(
            currentIcon = wishlist?.icon ?: "card_giftcard",
            currentColor = wishlist?.color,
            onDismiss = { showAppearanceDialog = false },
            onConfirm = { icon, color ->
                viewModel.changeWishlistIcon(wishlistId, icon)
                viewModel.changeWishlistColor(wishlistId, color)
                showAppearanceDialog = false
            },
        )
    }

    if (showAddWish) {
        AddWishDialog(
            onDismiss = { showAddWish = false },
            onConfirm = { draft ->
                viewModel.addWish(context, wishlistId, draft)
                showAddWish = false
            },
        )
    }

    wishToEdit?.let { editing ->
        AddWishDialog(
            initial = editing,
            onDismiss = { wishToEdit = null },
            onConfirm = { draft ->
                viewModel.updateWish(context, editing.id, draft)
                wishToEdit = null
            },
        )
    }

    detailWish?.let { wish ->
        WishDetailDialog(
            wish = wish,
            reservation = reservations[wish.id],
            currentUserId = currentUserId,
            onReserve = { viewModel.reserve(wish) },
            onUnreserve = { viewModel.unreserve(wish) },
            onDismiss = { detailWish = null },
        )
    }
}

/** Everything about one wish, for members viewing someone else's list: full-size image,
 *  name, description, price, the FULL link (tappable), and the reserve control. */
@Composable
private fun WishDetailDialog(
    wish: WishModel,
    reservation: WishReservationModel?,
    currentUserId: String?,
    onReserve: () -> Unit,
    onUnreserve: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val reservedByMe = reservation != null && reservation.reservedBy == currentUserId
    val reservedByOther = reservation != null && !reservedByMe
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wish.text, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!wish.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = wish.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(Radius.field)),
                    )
                }
                wish.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                wish.price?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        formatWishPrice(it),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                wish.link?.takeIf { it.isNotBlank() }?.let { link ->
                    Text(
                        link,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier.clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                            },
                    )
                }
                when {
                    reservedByMe -> Text(stringResource(R.string.reserved_by_you), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    reservedByOther -> Text(stringResource(R.string.reserved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            when {
                reservedByMe -> TextButton(onClick = { onUnreserve(); onDismiss() }) { Text(stringResource(R.string.unreserve)) }
                !reservedByOther -> TextButton(onClick = { onReserve(); onDismiss() }) { Text(stringResource(R.string.reserve)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

// ─── Share / export helpers ────────────────────────────────────────────────────

/** Mints the share link and opens the Android share sheet with a friendly message. */
private suspend fun shareWishlistLink(
    context: Context,
    viewModel: WishlistViewModel,
    wishlistId: String,
    name: String?,
) {
    val url = viewModel.shareLink(wishlistId) ?: return
    val listName = name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wishlist)
    val message = context.getString(R.string.share_wishlist_message, listName) + "\n" + url
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_wishlist_chooser)))
}

/** Builds the PDF off the main thread, then opens the Android share sheet via FileProvider. */
private suspend fun exportWishlistPdf(
    context: Context,
    wishlist: WishlistModel?,
    wishes: List<WishModel>,
) {
    val name = wishlist?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.wishlist)
    val ownerName = wishlist?.ownerName.orEmpty()
    val subtitle = if (ownerName.isEmpty()) "" else context.getString(R.string.by_owner, ownerName)
    val file = withContext(Dispatchers.IO) { WishlistPdf.write(context, name, subtitle, wishes) } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_wishlist_pdf_chooser)))
}

// ─── Wish rows ─────────────────────────────────────────────────────────────────

/** Title with the price appended inline (e.g. "Lego set  ·  $50"). */
private fun wishTitle(wish: WishModel): String =
    wish.text + (wish.price?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: "")

/** Leading wish image thumbnail, shown only when the wish has an image. */
@Composable
private fun RowScope.WishLeadingThumb(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
    }
}

/** Opens the wish's link in the browser; shown only when the wish has a link. */
@Composable
private fun WishLinkButton(link: String?) {
    if (!link.isNullOrBlank()) {
        val context = LocalContext.current
        IconButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
        }) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open_link),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A wish as seen by the owner: claim circle, tap-to-edit body, link. */
@Composable
private fun OwnerWishRow(
    wish: WishModel,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .rowSurface(ghost = wish.checked, cornerRadius = Radius.row)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "${wish.text}, ${if (wish.checked) "claimed" else "unclaimed"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (wish.checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = stringResource(if (wish.checked) R.string.unmark_as_claimed else R.string.mark_as_claimed),
                tint = if (wish.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WishLeadingThumb(wish.imageUrl)
        Text(
            wishTitle(wish),
            Modifier.weight(1f).clickable { onEdit() },
            style = MaterialTheme.typography.bodyLarge,
            color = if (wish.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (wish.checked) TextDecoration.LineThrough else TextDecoration.None,
        )
        WishLinkButton(wish.link)
    }
}

/** A wish as seen by a NON-owner family member: title/price + reserve/claim control.
 *  Reservation state is hidden from the wishlist owner at the DB level (RLS). */
@Composable
private fun MemberWishCard(
    wish: WishModel,
    reservation: WishReservationModel?,
    currentUserId: String?,
    onReserve: () -> Unit,
    onUnreserve: () -> Unit,
    onShowDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reservedByMe = reservation != null && reservation.reservedBy == currentUserId
    val reservedByOther = reservation != null && !reservedByMe
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.row))
            .clickable(onClick = onShowDetail)
            .rowSurface(ghost = reservedByOther, cornerRadius = Radius.row)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WishLeadingThumb(wish.imageUrl)
        Column(Modifier.weight(1f)) {
            Text(
                wish.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (reservedByOther) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            wish.price?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WishLinkButton(wish.link)
        Spacer(Modifier.width(4.dp))
        when {
            reservedByMe ->
                TextButton(onClick = onUnreserve) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reserved_by_you))
                }
            reservedByOther ->
                Row(
                    Modifier.padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reserved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            else -> TextButton(onClick = onReserve) { Text(stringResource(R.string.reserve)) }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

private val DEFAULT_WISHLIST_COLOR = AppColorPalette.first()

@Composable
private fun NewWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: Int?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("card_giftcard") }
    var color by remember { mutableStateOf<Int?>(DEFAULT_WISHLIST_COLOR) }

    // iOS-parity creation sheet (NewWishlistSheet).
    CreationSheet(
        title = stringResource(R.string.new_wishlist),
        confirmTitle = stringResource(R.string.create),
        confirmEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim(), selectedIcon, color) },
    ) {
        SheetField(
            icon = IconKeyMap.wishlist(selectedIcon),
            placeholder = stringResource(R.string.wishlist_name),
            value = name,
            onValueChange = { name = it },
        )
        SheetSectionLabel(stringResource(R.string.icon))
        IconGrid(
            options = IconOptions.wishlist,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Wishlists,
            colorOverride = hexColor(color),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = color, onSelect = { color = it })
    }
}

@Composable
private fun RenameWishlistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    CreationSheet(
        title = stringResource(R.string.rename_wishlist),
        confirmTitle = stringResource(R.string.save),
        confirmEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(name.trim()) },
    ) {
        SheetField(
            icon = Icons.Filled.Edit,
            placeholder = stringResource(R.string.wishlist_name),
            value = name,
            onValueChange = { name = it },
        )
    }
}

/** Owner-only appearance editor: pick both icon and colour in one sheet. */
@Composable
private fun WishlistAppearanceDialog(
    currentIcon: String,
    currentColor: Int?,
    onDismiss: () -> Unit,
    onConfirm: (icon: String, color: Int?) -> Unit,
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }
    var color by remember { mutableStateOf(currentColor ?: DEFAULT_WISHLIST_COLOR) }

    CreationSheet(
        title = stringResource(R.string.change_icon),
        confirmTitle = stringResource(R.string.save),
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selectedIcon, color) },
    ) {
        SheetSectionLabel(stringResource(R.string.icon))
        IconGrid(
            options = IconOptions.wishlist,
            selected = selectedIcon,
            onSelect = { selectedIcon = it },
            feature = FeatureAccent.Wishlists,
            colorOverride = hexColor(color),
        )
        SheetSectionLabel(stringResource(R.string.color))
        ColorPickerRow(selected = color, onSelect = { color = it })
    }
}

/** Rich add/edit-a-wish flow: title (required) + optional link, price, and image. */
@Composable
private fun AddWishDialog(
    onDismiss: () -> Unit,
    onConfirm: (WishDraft) -> Unit,
    initial: WishModel? = null,
) {
    var title by remember { mutableStateOf(initial?.text ?: "") }
    var link by remember { mutableStateOf(initial?.link ?: "") }
    var price by remember { mutableStateOf(initial?.price ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var image by remember { mutableStateOf<Uri?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) image = uri
        }

    // iOS-parity wish sheet (AddWishSheet / EditWishSheet): title, link, price, photo.
    CreationSheet(
        title = stringResource(if (initial != null) R.string.edit_wish else R.string.add_a_wish),
        confirmTitle = stringResource(if (initial != null) R.string.save else R.string.add),
        confirmEnabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(WishDraft(title.trim(), link, price, image, description)) },
    ) {
        SheetField(
            icon = Icons.Filled.Redeem,
            placeholder = stringResource(R.string.what_do_you_wish_for),
            value = title,
            onValueChange = { title = it },
        )
        SheetField(
            icon = Icons.Filled.Notes,
            placeholder = stringResource(R.string.description_optional),
            value = description,
            onValueChange = { description = it },
        )
        SheetField(
            icon = Icons.Filled.Link,
            placeholder = stringResource(R.string.link_optional),
            value = link,
            onValueChange = { link = it },
        )
        SheetField(
            icon = Icons.Filled.LocalOffer,
            placeholder = stringResource(R.string.price_optional),
            value = price,
            onValueChange = { price = it },
        )
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        // Dashed "Add photo (optional)" row (mirrors the iOS wish sheet).
        Row(
            Modifier
                .fillMaxWidth()
                .ghostSurface(Radius.field)
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                stringResource(
                    if (image == null && initial?.imageUrl.isNullOrBlank()) {
                        R.string.add_photo_optional
                    } else {
                        R.string.change_photo
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun dialogFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )
