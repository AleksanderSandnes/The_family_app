package com.sandnes.familyapp.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design-system spacing tokens — the single source of truth for padding and gaps.
 * Based on a 4-pt grid. Screens must reference these instead of hardcoding dp values.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Canonical layout values used app-wide. */
    val screenEdge = 20.dp
    val cardPadding = 18.dp
    val cardGap = 12.dp
}

/** Two-level elevation scale — resting surfaces and raised elements (FAB/sheets). */
object Elevation {
    val resting = 2.dp
    val raised = 6.dp
}

/** Component corner-radius tokens (complement [AppShapes]). Mirrors iOS `Radius`. */
object Radius {
    val extraSmall = 10.dp
    val small = 14.dp
    val medium = 20.dp
    val large = 28.dp
    val extraLarge = 36.dp

    val card = 20.dp
    val field = 16.dp
    val button = 18.dp
    val sheet = 28.dp

    // ── Liquid Glass scale (1c) ───────────────────────────────────────────────
    val badge = 12.dp // icon badges (12–14)
    val badgeLarge = 14.dp
    val row = 20.dp // list rows / grid tiles / bubbles
    val overviewCard = 22.dp // overview cards
    val bigCard = 26.dp // family card, month grid, hero, meal-week header
    val tabBar = 33.dp // floating tab bar
    val fab = 26.dp // extended FAB (h52)
    val segment = 14.dp // segmented control track
    val segmentThumb = 11.dp
    val menu = 18.dp // ⋯ popover menus
}

/** Motion duration tokens (ms). Mirrors the M18 navigation transitions. */
object Motion {
    const val SLIDE_MS = 300
    const val FADE_MS = 200
}
