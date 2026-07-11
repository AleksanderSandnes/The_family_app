@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials

/*
 * Liquid Glass design layer for Android (mirrors iOS `DesignSystem/Glass.swift`).
 *
 * Look: translucent blurred "glass" surfaces floating over an ambient radial-wash background.
 * On API 31+ the blur is real (Haze → RenderEffect). Below 31 every glass helper degrades to a
 * translucent solid surface + elevation, so the UI stays legible and on-brand without blur.
 *
 * Usage: wrap a screen (or the app scaffold) in [AmbientBackground], then apply
 * [glassCard]/[glassChrome]/etc. to surfaces inside it.
 */

/** Whether the platform can render real-time blur (Haze needs RenderEffect, API 31+). */
val supportsBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Shared background-only [HazeState] for the current [AmbientBackground] subtree — its single
 * source is the ambient wash. Cards and in-screen chrome sample this, so they blur the wash
 * behind them and never their own scroll container (self-sampling causes smudge artifacts).
 * Null when no ambient background is present (e.g. previews) — glass helpers then use the
 * fallback path.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * [HazeState] for floating chrome (the tab bar): sources are the ambient wash **and** the
 * screen content marked with [glassSource], so chrome blurs content scrolling beneath it.
 */
val LocalChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Ambient canvas — three soft radial washes (violet top-right, indigo left, teal bottom) over a
 * near-white / near-black base. Provides the background [HazeState] (sampled by [glassCard])
 * and the chrome [HazeState] (sampled by [glassBar]), with the wash registered in both.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = remember { HazeState() }
    val chromeState = remember { HazeState() }
    val dark = appDarkTheme()
    val base = if (dark) AmbientBaseDark else AmbientBaseLight

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalChromeHazeState provides chromeState,
    ) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .hazeSource(chromeState, zIndex = -1f)
                    .drawBehind {
                        drawRect(base)
                        drawWash(Color(0xFF7C3AED), if (dark) 0.32f else 0.17f, Offset(0.88f * size.width, 0.06f * size.height), 0.85f * size.width)
                        drawWash(Color(0xFF5457E8), if (dark) 0.28f else 0.16f, Offset(-0.05f * size.width, 0.30f * size.height), 1.05f * size.width)
                        drawWash(Color(0xFF14B8A6), if (dark) 0.18f else 0.13f, Offset(0.60f * size.width, 1.02f * size.height), 0.98f * size.width)
                    },
            )
            content()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWash(
    color: Color,
    alpha: Float,
    center: Offset,
    radius: Float,
) {
    val tinted = color.copy(alpha = alpha)
    // Brush coordinates are canvas-absolute, so the gradient must be centred on the wash
    // centre itself (not rect-relative) — otherwise the washes land in the wrong place.
    val brush =
        Brush.radialGradient(
            colors = listOf(tinted, tinted.copy(alpha = 0f)),
            center = center,
            radius = radius,
        )
    drawRect(
        brush = brush,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        blendMode = BlendMode.Plus,
    )
}

// ── Glass surface modifiers ──────────────────────────────────────────────────

private val glassShadow = Color(0xFF141A3C)

@Composable
@ReadOnlyComposable
private fun glassFallbackFill(dark: Boolean): Color =
    if (dark) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f)

// The separator hairline. In light mode a WHITE edge is invisible on the near-white ambient
// wash — iOS's system glass draws a dark separator on the light side, so we use a dark ink edge
// here. That single change is the biggest fix for the "washed-out / can't distinguish cards" look.
@Composable
@ReadOnlyComposable
private fun glassHairline(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF16192A).copy(alpha = 0.10f)

/** Content card / list row / grid tile — the workhorse glass surface (recipe A). */
@Composable
fun Modifier.glassCard(cornerRadius: Dp = Radius.card): Modifier = glassSurface(cornerRadius, tint = null)

/** Accent-tinted glass card — for selected / active surfaces. */
@Composable
fun Modifier.glassCardTinted(
    tint: Color,
    cornerRadius: Dp = Radius.card,
): Modifier =
    glassSurface(cornerRadius, tint = tint)

/** Nav / chrome pill or floating control (recipe B) — thinner material feel. */
@Composable
fun Modifier.glassChrome(cornerRadius: Dp): Modifier = glassSurface(cornerRadius, tint = null, chrome = true)

/**
 * Floating bar chrome (the tab bar) — samples the chrome [HazeState] so it blurs screen
 * content scrolling beneath it, not just the ambient wash.
 */
@Composable
fun Modifier.glassBar(cornerRadius: Dp): Modifier =
    glassSurface(cornerRadius, tint = null, chrome = true, state = LocalChromeHazeState.current)

/**
 * Marks scrollable content as a blur source so floating chrome (tab bar) blurs it.
 * No-op when no [AmbientBackground] is present.
 */
@Composable
fun Modifier.glassSource(): Modifier {
    val haze = LocalChromeHazeState.current
    return if (haze != null) this.hazeSource(haze, zIndex = 0f) else this
}

@Composable
private fun Modifier.glassSurface(
    cornerRadius: Dp,
    tint: Color?,
    chrome: Boolean = false,
    state: HazeState? = null,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val haze = state ?: LocalHazeState.current
    val dark = appDarkTheme()
    val shadowColor = tint ?: glassShadow
    // Android renders colored elevation shadows faint; bump alpha so cards clearly lift off the
    // light ambient wash (approximates iOS's r9/y6 soft drop shadow).
    val shadowAlpha = if (tint != null) 0.22f else 0.12f
    val hairline = glassHairline(dark)

    val base =
        this
            .shadow(
                elevation = if (chrome) Elevation.raised else 10.dp,
                shape = shape,
                clip = false,
                ambientColor = shadowColor.copy(alpha = shadowAlpha),
                spotColor = shadowColor.copy(alpha = shadowAlpha),
            ).clip(shape)

    val filled =
        if (supportsBlur && haze != null) {
            val style = if (chrome) HazeMaterials.thin() else HazeMaterials.regular()
            base
                .hazeEffect(state = haze, style = style)
                .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.16f)) else Modifier)
        } else {
            base.background(tint?.copy(alpha = 0.20f) ?: glassFallbackFill(dark))
        }

    return filled.drawBehind {
        val r = cornerRadius.toPx()
        // Inset the stroke by half its width so a real ~1.5dp hairline isn't clipped at the edge.
        val w = 1.5.dp.toPx()
        val half = w / 2
        drawRoundRect(
            color = hairline,
            topLeft = Offset(half, half),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(r - half, r - half),
            style = Stroke(width = w),
        )
    }
}

/** Glass (active) or dashed ghost (completed / empty) for a list-row surface. */
@Composable
fun Modifier.rowSurface(
    ghost: Boolean,
    cornerRadius: Dp = Radius.row,
): Modifier =
    if (ghost) ghostSurface(cornerRadius) else glassCard(cornerRadius)

/**
 * Ghost / empty surface — completed or unplanned items recede: no glass material, a translucent
 * fill and a dashed hairline, no drop shadow. Mirrors iOS `ghostSurface`.
 */
@Composable
fun Modifier.ghostSurface(cornerRadius: Dp = Radius.card): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val dark = appDarkTheme()
    val fill = if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.36f)
    val stroke = if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF5F6780).copy(alpha = 0.28f)
    return this
        .clip(shape)
        .background(fill)
        .drawBehind {
            val r = cornerRadius.toPx()
            drawRoundRect(
                color = stroke,
                cornerRadius = CornerRadius(r, r),
                style =
                    Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
                    ),
            )
        }
}
