package com.example.mainactivity.ui.theme

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

/** Component corner-radius tokens (complement [AppShapes]). */
object Radius {
    val card = 20.dp
    val field = 16.dp
    val button = 18.dp
    val sheet = 28.dp
}

/** Motion duration tokens (ms). Mirrors the M18 navigation transitions. */
object Motion {
    const val SLIDE_MS = 300
    const val FADE_MS = 200
}
