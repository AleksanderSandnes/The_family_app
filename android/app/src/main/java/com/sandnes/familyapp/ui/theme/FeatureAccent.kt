package com.sandnes.familyapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Feature-accent palette. Feature colours live ONLY in icon badges and
 * calendar event dots — never on large surfaces. One entry per domain; each resolves an icon
 * [stroke], a translucent [badgeFill], and a fixed calendar [dot], adapting light ↔ dark.
 *
 * Mirrors iOS `FeatureAccent`. Light/dark is resolved via [isSystemInDarkTheme] at read time,
 * so these are `@Composable` accessors rather than plain vals.
 */
enum class FeatureAccent {
    Shopping,
    Meals,
    Calendar,
    Birthdays,
    Wishlists,
    Map,
    Chat,
    Family,
    ;

    /** Icon-glyph stroke colour (darker in light, brighter in dark). */
    @Composable
    @ReadOnlyComposable
    fun stroke(): Color {
        val dark = appDarkTheme()
        return when (this) {
            Shopping, Chat -> if (dark) Color(0xFFA5ABFF) else Color(0xFF4F55E6)
            Meals -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
            Calendar -> if (dark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
            Birthdays -> if (dark) Color(0xFFF472B6) else Color(0xFFDB2777)
            Wishlists, Family -> if (dark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
            Map -> if (dark) Color(0xFF34D399) else Color(0xFF059669)
        }
    }

    /** Translucent badge fill (12–15% in light, 16% in dark). */
    @Composable
    @ReadOnlyComposable
    fun badgeFill(): Color {
        val dark = appDarkTheme()
        return when (this) {
            Shopping, Chat ->
                if (dark) Color(0xFFA5ABFF).copy(alpha = 0.16f) else Color(0xFF6366F1).copy(alpha = 0.14f)
            Meals ->
                if (dark) Color(0xFFFBBF24).copy(alpha = 0.16f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
            Calendar ->
                if (dark) Color(0xFF2DD4BF).copy(alpha = 0.16f) else Color(0xFF14B8A6).copy(alpha = 0.14f)
            Birthdays ->
                if (dark) Color(0xFFF472B6).copy(alpha = 0.16f) else Color(0xFFEC4899).copy(alpha = 0.13f)
            Wishlists, Family ->
                if (dark) Color(0xFFC4B5FD).copy(alpha = 0.16f) else Color(0xFF8B5CF6).copy(alpha = 0.14f)
            Map ->
                if (dark) Color(0xFF34D399).copy(alpha = 0.16f) else Color(0xFF10B981).copy(alpha = 0.14f)
        }
    }

    /** Calendar event-dot colour (fixed, saturated — same in light and dark). */
    val dot: Color
        get() =
            when (this) {
                Shopping, Chat -> Color(0xFF4F55E6)
                Meals -> Color(0xFFF59E0B)
                Calendar -> Color(0xFF14B8A6)
                Birthdays -> Color(0xFFEC4899)
                Wishlists, Family -> Color(0xFF8B5CF6)
                Map -> Color(0xFF10B981)
            }
}
