@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sandnes.familyapp.ui.theme.AppColorPalette
import com.sandnes.familyapp.ui.theme.FeatureAccent
import com.sandnes.familyapp.ui.theme.FeatureBadge
import com.sandnes.familyapp.ui.theme.IconKeyMap
import com.sandnes.familyapp.ui.theme.Radius

/**
 * Horizontal row of the 8 shared item colours. [selected] is a 0xRRGGBB int (nullable → nothing
 * selected). Mirrors iOS `EventColorPicker`. Used by every colour picker (shopping/meal/wishlist/
 * calendar/birthday) so the palette stays consistent across features.
 */
@Composable
fun ColorPickerRow(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppColorPalette.forEach { hex ->
            val swatch = Color(0xFF000000.toInt() or hex)
            val isSelected = selected == hex
            val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "swatch")
            Box(
                Modifier
                    .scale(scale)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .then(if (isSelected) Modifier.border(2.dp, swatch, CircleShape) else Modifier)
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Wrapping grid of DB icon-key options rendered as [FeatureBadge]s. [selected] is the current
 * DB icon key. Mirrors iOS `IconGrid`. Pass the feature so unselected badges use the domain accent,
 * or [colorOverride] to tint with the user-picked colour.
 */
@Composable
fun IconGrid(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    feature: FeatureAccent,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { key ->
            val isSelected = key == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.badgeLarge))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, colorOverride ?: feature.stroke(), RoundedCornerShape(Radius.badgeLarge))
                        } else {
                            Modifier
                        },
                    ).clickable { onSelect(key) },
            ) {
                FeatureBadge(
                    icon = IconKeyMap.icon(key, Icons.Filled.Star),
                    feature = feature,
                    size = 46.dp,
                    colorOverride = colorOverride,
                )
            }
        }
    }
}
