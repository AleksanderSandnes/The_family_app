package com.example.mainactivity.ui.home

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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.ui.components.InitialAvatar
import com.example.mainactivity.ui.theme.heroGradient

private data class Feature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenFamily: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    val features = listOf(
        Feature("Shopping", "Shared lists", Icons.Filled.ShoppingCart, Color(0xFF6366F1), "shopping"),
        Feature("Meals", "Plan the week", Icons.Filled.Restaurant, Color(0xFFF59E0B), "meal"),
        Feature("Calendar", "Family events", Icons.Filled.CalendarMonth, Color(0xFF14B8A6), "calendar"),
        Feature("Birthdays", "Never miss one", Icons.Filled.Cake, Color(0xFFEC4899), "birthday"),
        Feature("Wishlists", "Gift ideas", Icons.Filled.CardGiftcard, Color(0xFF8B5CF6), "wishlist"),
        Feature("Family chat", "Stay close", Icons.AutoMirrored.Filled.Chat, Color(0xFF06B6D4), "chat")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                val greeting = state.user?.name?.substringBefore(' ').orEmpty()
                Text(
                    "Hi${if (greeting.isNotBlank()) ", $greeting" else ""} 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Here's everything your family shares.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FamilyCard(
                    familyName = state.family?.name,
                    memberCount = state.memberCount,
                    avatarColor = Color(if (state.user?.avatarColor != 0) state.user?.avatarColor ?: 0xFF6366F1.toInt() else 0xFF6366F1.toInt()),
                    userName = state.user?.name ?: "",
                    avatarUri = state.user?.avatarUri,
                    dark = dark,
                    onClick = onOpenFamily
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        items(features) { feature ->
            FeatureTile(feature, onClick = { onOpen(feature.route) })
        }
    }
}

@Composable
private fun FamilyCard(
    familyName: String?,
    memberCount: Int,
    avatarColor: Color,
    userName: String,
    avatarUri: String?,
    dark: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(heroGradient(dark))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    familyName ?: "Set up your family",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (familyName != null) "$memberCount member${if (memberCount == 1) "" else "s"}" else "Create or join a family group",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            InitialAvatar(userName.ifBlank { "?" }, avatarColor, size = 40, avatarUri = avatarUri)
        }
    }
}

@Composable
private fun FeatureTile(feature: Feature, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.05f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(feature.color, feature.color.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column {
                Text(feature.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(feature.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
