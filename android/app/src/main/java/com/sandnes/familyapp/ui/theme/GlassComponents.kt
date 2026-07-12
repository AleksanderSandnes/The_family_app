@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A rounded feature icon badge — translucent domain-coloured fill with the glyph in the domain
 * stroke colour. Feature colour lives only here (and calendar dots). Mirrors iOS `FeatureBadge`.
 *
 * @param colorOverride when set, replaces the feature palette with a user-picked colour (used by
 *   the colour pickers on shopping lists, meal plans, wishlists, calendar events, birthdays).
 */
@Composable
fun FeatureBadge(
    icon: ImageVector,
    feature: FeatureAccent,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    cornerRadius: Dp = Radius.badge,
    colorOverride: Color? = null,
) {
    val fill = colorOverride?.copy(alpha = 0.16f) ?: feature.badgeFill()
    val glyph = colorOverride ?: feature.stroke()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            // Opaque backing first: the pastel fill is translucent, and it otherwise composites
            // over the translucent glass card → near-invisible. Backing on `surface` keeps the
            // domain hue readable (identical look to iOS's badge on opaque system glass).
            .background(MaterialTheme.colorScheme.surface)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(size * 0.44f))
    }
}

/**
 * Thin progress bar — accent (or feature) track at ~13% with a solid fill. Mirrors iOS
 * `GlassProgressBar`.
 */
@Composable
fun GlassProgressBar(
    value: Float, // 0…1
    modifier: Modifier = Modifier,
    tint: Color = Accent,
    height: Dp = 4.dp,
) {
    val clamped = value.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(CircleShape)
                .background(tint),
        )
    }
}
