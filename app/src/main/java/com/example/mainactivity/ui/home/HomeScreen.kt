@file:Suppress("ktlint:standard:function-naming")

package com.example.mainactivity.ui.home

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mainactivity.ui.components.ErrorBanner
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.components.LoadingState
import com.example.mainactivity.ui.components.RefreshOnResume
import com.example.mainactivity.ui.theme.Amber500
import com.example.mainactivity.ui.theme.Emerald500
import com.example.mainactivity.ui.theme.Indigo500
import com.example.mainactivity.ui.theme.Pink500
import com.example.mainactivity.ui.theme.Teal500
import com.example.mainactivity.ui.theme.Violet500
import com.example.mainactivity.ui.theme.heroGradient

private data class Feature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String,
)

// All feature values are compile-time constants — hoisted to avoid reallocating on every recomposition.
private val features =
    listOf(
        Feature("Shopping", "Shared lists", Icons.Filled.ShoppingCart, Indigo500, "shopping"),
        Feature("Meals", "Plan the week", Icons.Filled.Restaurant, Amber500, "meal"),
        Feature("Calendar", "Family events", Icons.Filled.CalendarMonth, Teal500, "calendar"),
        Feature("Birthdays", "Never miss one", Icons.Filled.Cake, Pink500, "birthday"),
        Feature("Wishlists", "Gift ideas", Icons.Filled.CardGiftcard, Violet500, "wishlist"),
        Feature("Family Map", "See where everyone is", Icons.Filled.Map, Emerald500, "family_map"),
    )

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenFamily: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()

    RefreshOnResume { viewModel.refresh() }

    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp =
        with(density) {
            windowInfo.containerSize.width
                .toDp()
                .value
        }
    val isWideWindow = screenWidthDp >= 600
    val isTablet = configuration.smallestScreenWidthDp >= 600
    // Compact width: 2 columns. Medium/Expanded (wide window): 3 columns.
    val columns = if (isWideWindow) 3 else 2
    val horizontalPadding = if (isTablet) 32.dp else 20.dp

    // Tiles must be shorter on larger screens so all 6 fit without scrolling.
    // 1.15 on phone portrait: tile ≈143dp tall with 14dp padding leaves 115dp for content
    // (44dp icon + 8dp gap + ~36dp text = 88dp) — fits with room to spare, no clipping.
    val tileAspectRatio =
        when {
            isLandscape && isTablet -> 2.2f
            isLandscape -> 1.5f
            isTablet -> 1.4f
            else -> 1.15f
        }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                HomeHeader(
                    state = state,
                    dark = dark,
                    onOpenFamily = onOpenFamily,
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        when {
            state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingState() }
            state.loadError ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBanner(message = "Couldn't load your data. Pull to refresh.")
                }
            else ->
                items(features) { feature ->
                    FeatureTile(
                        feature = feature,
                        aspectRatio = tileAspectRatio,
                        onClick = { onOpen(feature.route) },
                    )
                }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeUiState,
    dark: Boolean,
    onOpenFamily: () -> Unit,
) {
    val user = state.user
    val firstName = user?.name?.substringBefore(' ').orEmpty()
    // Computed once per composition — changes at most twice per day
    val greeting = remember { timeBasedGreeting() }
    // Use a visible fallback color when avatarColor is 0 (never configured)
    val avatarColor =
        if (user != null && user.avatarColor != 0) {
            Color(user.avatarColor)
        } else {
            MaterialTheme.colorScheme.primary
        }

    Column {
        // Greeting row with avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (firstName.isNotBlank()) "$greeting, $firstName" else greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Here's everything your family shares.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user != null) {
                Spacer(Modifier.size(12.dp))
                InitialAvatar(
                    name = user.name,
                    color = avatarColor,
                    avatarUri = user.avatarUrl,
                    size = 48,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Family card — gradient hero if family exists, CTA banner if signed-in but no family,
        // or nothing while still loading (user == null)
        if (user != null) {
            if (state.family != null) {
                FamilyCard(
                    familyName = state.family.name,
                    memberCount = state.memberCount,
                    dark = dark,
                    photoUrl = state.family.photoUrl,
                    onClick = onOpenFamily,
                )
            } else {
                NoFamilyBanner(onOpenFamily = onOpenFamily)
            }
        }
    }
}

@Composable
private fun FamilyCard(
    familyName: String,
    memberCount: Int,
    dark: Boolean,
    photoUrl: String?,
    onClick: () -> Unit,
) {
    // Surface provides the click handler and ripple; Box carries the gradient background.
    // shadowElevation on Surface requires an opaque color — use primaryContainer as the
    // surface color so the shadow renders, then overdraw with the gradient inside.
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(heroGradient(dark))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.Groups, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = familyName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$memberCount member${if (memberCount == 1) "" else "s"}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoFamilyBanner(onOpenFamily: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No family yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Join or create a family to get started",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(
                onClick = onOpenFamily,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text("Get started", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FeatureTile(
    feature: Feature,
    aspectRatio: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "tile-press",
    )
    // Memoised per feature color — avoids allocation on every animation frame
    val iconBrush =
        remember(feature.color) {
            Brush.linearGradient(listOf(feature.color, feature.color.copy(alpha = 0.7f)))
        }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .scale(scale)
                .semantics {
                    role = Role.Button
                    contentDescription = "${feature.title} feature"
                },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(iconBrush, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
