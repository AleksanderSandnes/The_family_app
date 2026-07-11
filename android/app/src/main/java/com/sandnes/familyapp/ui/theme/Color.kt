package com.sandnes.familyapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand — refined indigo / violet
val Indigo50 = Color(0xFFEEF0FF)
val Indigo100 = Color(0xFFE0E3FF)
val Indigo200 = Color(0xFFC4C9FF)
val Indigo300 = Color(0xFF9CA3FF)
val Indigo500 = Color(0xFF6366F1)
val Indigo600 = Color(0xFF5457E8)
val Indigo700 = Color(0xFF4338CA)
val Violet500 = Color(0xFF8B5CF6)
val Violet600 = Color(0xFF7C3AED)

// Accent
val Pink500 = Color(0xFFEC4899)
val Teal500 = Color(0xFF14B8A6)
val Amber500 = Color(0xFFF59E0B)
val Emerald500 = Color(0xFF10B981)
val Rose500 = Color(0xFFF43F5E)

// Neutrals (light)
val Slate900 = Color(0xFF0B1020)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Canvas = Color(0xFFF7F8FC)
val SurfaceLight = Color(0xFFFFFFFF)

// Neutrals (dark)
val Ink = Color(0xFF0A0E1A)
val InkSurface = Color(0xFF141A2A)
val InkSurfaceVariant = Color(0xFF1E2638)
val InkBorder = Color(0xFF2A3349)
val InkText = Color(0xFFE8EBF5)
val InkTextMuted = Color(0xFF9AA4BE)

// ── Liquid Glass ─────────────────────────────────────────────────────────────
// Single interactive accent — a quieted indigo. Brightens in dark for legibility.
val Accent = Color(0xFF4F55E6)
val AccentDark = Color(0xFFA5ABFF) // brightened accent for dark text/icons
val AccentActiveTab = Color(0xFFC9CDFF) // active tab glyph in dark

// Ambient canvas bases — the three radial washes render over these (see AmbientBackground).
val AmbientBaseLight = Color(0xFFEFF1F8)
val AmbientBaseDark = Color(0xFF0B0D16)

// Text ramp — primary / secondary / caption, light & dark.
val InkLight = Color(0xFF16192A)
val InkDark = Color(0xFFECEEF8)
val SecondaryLight = Color(0xFF5F6780)
val SecondaryDark = Color(0xFF98A0BC)
val CaptionLight = Color(0xFF8B92AC)
val CaptionDark = Color(0xFF6A7290)

// Secondary containers (violet).
val VioletContainerLight = Color(0xFFEDE4FF)
val VioletContainerDark = Color(0xFF4C1D95)

// Status — mirror iOS Palette. Destructive/live/urgency/map tokens.
val Destructive = Color(0xFFE11D48) // menu rows, leave/sign-out, delete
val LiveGreen = Color(0xFF10B981) // today / live / done
val LiveGreenText = Color(0xFF059669) // "All done" text
val WeekAmberText = Color(0xFFB45309) // "this week" urgency text
val StaleDot = Color(0xFFCBD2E0) // map stale dot
val MapBase = Color(0xFFE7ECE3) // family-map flat base

// Semantic status roles — use these instead of raw accent colors at call sites.
val Success = Emerald500
val Warning = Amber500
val Danger = Rose500

/**
 * The 8 user-selectable item colours (0xRRGGBB), shared by the calendar/shopping/meal/wishlist/
 * birthday colour pickers. Mirrors iOS `calendarEventColorPalette` — keep in sync.
 */
val AppColorPalette: List<Int> =
    listOf(
        0x6366F1, // indigo
        0x8B5CF6, // violet
        0xEC4899, // pink
        0x3B82F6, // blue
        0x14B8A6, // teal
        0x22C55E, // green
        0xF59E0B, // amber
        0xEF4444, // red
    )

/**
 * A user-picked colour stored as a 0xRRGGBB int (calendar/meal/shopping/wishlist/birthday
 * `color` columns) → opaque [Color]. Returns null when the value is null so call sites can
 * fall back to the feature accent. Mirrors iOS `hexColor(_:)`.
 */
@Suppress("MagicNumber") // packed 0xAARRGGBB bit manipulation — masks are self-describing
fun hexColor(value: Int?): Color? =
    value?.let { Color(0xFF000000.toInt() or (it and 0x00FFFFFF)) }

/**
 * ARGB int from the DB `avatar_color` column (produced by Kotlin `Color.toArgb()` on Android
 * and mirrored on iOS via `javaHashCode`) → [Color]. Mirrors iOS `Color(argb:)`.
 */
fun colorFromArgb(argb: Int): Color = Color(argb)

// Feature-accent palette — one stable identity color per domain. Keeps each
// feature recognizable (home tiles, icon chips, calendar event colors, etc.).
val FeatureShopping = Indigo500
val FeatureMeals = Amber500
val FeatureCalendar = Emerald500
val FeatureBirthdays = Pink500
val FeatureWishlists = Violet500
val FeatureMap = Teal500
val FeatureChat = Indigo500
val FeatureFamily = Violet600

// Signature gradients.
// brandGradient is the ONLY sanctioned gradient and may appear only on identity
// surfaces: hero headers, outgoing chat bubbles, the brand primary CTA, and the
// Home family banner. Do not use as a generic card/background fill.
val BrandGradient = Brush.linearGradient(listOf(Indigo600, Violet600))
val BrandGradientSoft = Brush.linearGradient(listOf(Indigo500, Violet500))

private val HeroDarkStart = Color(0xFF3730A3)
private val HeroDarkEnd = Color(0xFF6D28D9)

fun heroGradient(dark: Boolean): Brush =
    if (dark) {
        Brush.linearGradient(listOf(HeroDarkStart, HeroDarkEnd))
    } else {
        Brush.linearGradient(listOf(Indigo600, Violet600))
    }
