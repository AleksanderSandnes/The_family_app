@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.home

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.CalendarEventModel
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.ui.components.ErrorBanner
import com.sandnes.familyapp.ui.components.InitialAvatar
import com.sandnes.familyapp.ui.components.ListSkeleton
import com.sandnes.familyapp.ui.components.RefreshOnResume
import com.sandnes.familyapp.ui.components.SectionHeader
import com.sandnes.familyapp.ui.navigation.Routes
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.Indigo600
import com.sandnes.familyapp.ui.theme.Radius
import com.sandnes.familyapp.ui.theme.Spacing
import com.sandnes.familyapp.ui.theme.appDarkTheme
import com.sandnes.familyapp.ui.theme.glassCard
import com.sandnes.familyapp.ui.theme.heroGradient
import com.sandnes.familyapp.ui.theme.hexColor

private data class Feature(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: FeatureAccent,
    val route: String,
)

// All feature values are compile-time constants — hoisted to avoid reallocating on every recomposition.
private val features =
    listOf(
        Feature(R.string.shopping, R.string.shared_lists, Icons.Filled.ShoppingCart, FeatureAccent.Shopping, Routes.SHOPPING),
        Feature(R.string.meals, R.string.plan_the_week, Icons.Filled.Restaurant, FeatureAccent.Meals, Routes.MEAL),
        Feature(R.string.calendar, R.string.family_events, Icons.Filled.CalendarMonth, FeatureAccent.Calendar, Routes.CALENDAR),
        Feature(R.string.birthdays, R.string.never_miss_one, Icons.Filled.Cake, FeatureAccent.Birthdays, Routes.BIRTHDAY),
        Feature(R.string.wishlists, R.string.gift_ideas, Icons.Filled.CardGiftcard, FeatureAccent.Wishlists, Routes.WISHLIST),
        Feature(R.string.family_map, R.string.see_where_everyone_is, Icons.Filled.Map, FeatureAccent.Map, Routes.FAMILY_MAP),
    )

private const val PRESSED_TILE_SCALE = 0.97f
private const val MAX_ATTENDEE_AVATARS = 3

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenFamily: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = appDarkTheme()

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
    val horizontalPadding = if (isTablet) Spacing.xxxl else Spacing.screenEdge

    val tileAspectRatio =
        when {
            isLandscape && isTablet -> 2.2f
            isLandscape -> 1.7f
            isTablet -> 1.7f
            // Mirrors the iOS FeatureTile (minHeight 112pt on a ~171pt-wide tile).
            else -> 1.6f
        }

    // Transparent so the app-level AmbientBackground shows through the glass surfaces.
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = Spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                HomeHeader(state = state, dark = dark, onOpenFamily = onOpenFamily)
                Spacer(Modifier.height(Spacing.xs))
            }
        }

        when {
            state.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) { ListSkeleton(rows = 3) }
            state.loadError ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBanner(message = stringResource(R.string.couldn_t_load_your_data_pull_to_refresh))
                }
            else -> {
                if (state.hasSummary) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SummarySection(state = state, onOpen = onOpen)
                    }
                }
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
}

@Composable
private fun SummarySection(
    state: HomeUiState,
    onOpen: (String) -> Unit,
) {
    val tonight = state.tonightMeal
    val event = state.nextEvent
    val birthday = state.nextBirthday
    // Summary detail lines are composed here so they follow the in-app locale.
    val labels =
        SummaryLabels(
            today = stringResource(R.string.today),
            tomorrow = stringResource(R.string.tomorrow),
            todayExclaim = stringResource(R.string.today_exclaim),
            inDaysFormat = stringResource(R.string.in_days_lower),
            turnsFormat = stringResource(R.string.turns_age),
        )
    val today = remember(state) { java.time.LocalDate.now() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (tonight != null) {
            SummaryCard(
                icon = Icons.Filled.Restaurant,
                accent = FeatureAccent.Meals,
                label = stringResource(R.string.today_header),
                value = tonight,
                detail = null,
            ) { onOpen(Routes.MEAL) }
        }
        if (event != null) {
            EventSummaryCard(
                event = event,
                detail = eventWhen(event, today, labels),
                members = state.familyMembers,
            ) { onOpen(Routes.CALENDAR) }
        }
        if (state.shoppingRemaining > 0) {
            SummaryCard(
                icon = Icons.Filled.ShoppingCart,
                accent = FeatureAccent.Shopping,
                label = stringResource(R.string.shopping_header),
                value = stringResource(R.string.count_left_to_buy, state.shoppingRemaining),
                detail = null,
            ) { onOpen(Routes.SHOPPING) }
        }
        if (birthday != null) {
            SummaryCard(
                icon = Icons.Filled.Cake,
                accent = FeatureAccent.Birthdays,
                label = stringResource(R.string.next_birthday),
                value = birthday.name,
                detail = state.nextBirthdayDate?.let { birthdayWhen(birthday, it, today, labels) },
            ) { onOpen(Routes.BIRTHDAY) }
        }
        Spacer(Modifier.height(Spacing.xs))
        SectionHeader(stringResource(R.string.quick_access))
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    accent: FeatureAccent,
    label: String,
    value: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = Radius.row)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureBadge(icon = icon, feature = accent, size = 38.dp, cornerRadius = Radius.badge)
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = accent.stroke(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** NEXT EVENT card — shows the event's own icon, colour and attendee avatars. */
@Composable
private fun EventSummaryCard(
    event: CalendarEventModel,
    detail: String?,
    members: List<UserModel>,
    onClick: () -> Unit,
) {
    val accentOverride = hexColor(event.color)
    // Creator first, then everyone tagged as "going with".
    val people =
        remember(event, members) {
            (listOf(event.userId) + event.attendeeIds)
                .distinct()
                .mapNotNull { id -> members.firstOrNull { it.id == id } }
                .take(MAX_ATTENDEE_AVATARS)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = Radius.row)
            .clickable(onClick = onClick)
            .padding(Spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureBadge(
            icon = IconKeyMap.calendar(event.icon),
            feature = FeatureAccent.Calendar,
            size = 38.dp,
            cornerRadius = Radius.badge,
            colorOverride = accentOverride,
        )
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.next_event),
                style = MaterialTheme.typography.labelMedium,
                color = accentOverride ?: FeatureAccent.Calendar.stroke(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                event.activity,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (people.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-10).dp),
                modifier = Modifier.padding(end = Spacing.sm),
            ) {
                people.forEach { person ->
                    val color =
                        if (person.avatarColor != 0) Color(person.avatarColor) else MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        InitialAvatar(name = person.name, color = color, avatarUri = person.avatarUrl, size = 30)
                    }
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val greeting = stringResource(remember { timeBasedGreeting() })
    val avatarColor =
        if (user != null && user.avatarColor != 0) Color(user.avatarColor) else MaterialTheme.colorScheme.primary

    Column {
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
                    text = stringResource(R.string.here_s_everything_your_family_shares),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user != null) {
                Spacer(Modifier.size(Spacing.md))
                InitialAvatar(name = user.name, color = avatarColor, avatarUri = user.avatarUrl, size = 44)
            }
        }

        Spacer(Modifier.height(Spacing.lg))

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
    // Identity surface — hairline shine border + soft indigo drop shadow (mirrors iOS FamilyCard).
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(Radius.bigCard),
                    clip = false,
                    ambientColor = (if (dark) Color.Black else Indigo600).copy(alpha = if (dark) 0.4f else 0.28f),
                    spotColor = (if (dark) Color.Black else Indigo600).copy(alpha = if (dark) 0.4f else 0.28f),
                ),
        shape = RoundedCornerShape(Radius.bigCard),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(heroGradient(dark))
                .padding(Spacing.xl),
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
                Spacer(Modifier.size(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = familyName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            if (memberCount == 1) {
                                stringResource(R.string.one_member)
                            } else {
                                stringResource(R.string.member_count, memberCount)
                            },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun NoFamilyBanner(onOpenFamily: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.medium),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.no_family_yet),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.join_or_create_a_family_to_get_started),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(
                onClick = onOpenFamily,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.get_started), fontWeight = FontWeight.SemiBold)
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
        targetValue = if (isPressed) PRESSED_TILE_SCALE else 1f,
        label = "tile-press",
    )
    val featureTitle = stringResource(feature.titleRes)
    val featureContentDescription = stringResource(R.string.feature_named, featureTitle)

    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .scale(scale)
            .glassCard(cornerRadius = Radius.row)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = featureContentDescription
            }.padding(Spacing.md),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FeatureBadge(icon = feature.icon, feature = feature.accent, size = 40.dp, cornerRadius = Radius.badgeLarge)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = featureTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(feature.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
