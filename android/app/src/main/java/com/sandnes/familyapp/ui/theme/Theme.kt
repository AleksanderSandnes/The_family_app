@file:Suppress("ktlint:standard:function-naming")

package com.sandnes.familyapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors =
    lightColorScheme(
        primary = Indigo600,
        onPrimary = Color.White,
        primaryContainer = Indigo100,
        onPrimaryContainer = Indigo700,
        secondary = Violet600,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE4FF),
        onSecondaryContainer = Violet600,
        tertiary = Pink500,
        onTertiary = Color.White,
        background = Canvas,
        onBackground = Slate900,
        surface = SurfaceLight,
        onSurface = Slate900,
        surfaceVariant = Slate100,
        // Slate600 (not Slate500): Slate500 on the light canvas is ~4.3:1, just under
        // WCAG AA 4.5:1 for body text. Slate600 clears it for all secondary text.
        onSurfaceVariant = Slate600,
        outline = Slate200,
        outlineVariant = Slate100,
        error = Rose500,
        onError = Color.White,
    )

private val DarkColors =
    darkColorScheme(
        primary = Indigo300,
        onPrimary = Ink,
        primaryContainer = Indigo700,
        onPrimaryContainer = Indigo100,
        secondary = Violet500,
        onSecondary = Ink,
        secondaryContainer = Color(0xFF4C1D95),
        onSecondaryContainer = Color(0xFFEDE4FF),
        tertiary = Pink500,
        onTertiary = Color.White,
        background = Ink,
        onBackground = InkText,
        // InkSurface (#141A2A) for cards/surfaces; InkSurfaceVariant (#1E2638) for nested containers
        surface = InkSurface,
        onSurface = InkText,
        surfaceVariant = InkSurfaceVariant,
        onSurfaceVariant = InkTextMuted,
        // surfaceTint is used by Material 3 tonal elevation overlays in dark mode
        surfaceTint = Indigo300,
        outline = InkBorder,
        outlineVariant = InkSurfaceVariant,
        error = Rose500,
        onError = Color.White,
    )

/**
 * The app-resolved dark flag (in-app theme setting, falling back to the system). The design
 * layer must read THIS — not [isSystemInDarkTheme] — or the ambient wash and glass surfaces
 * stay light when the user picks Dark in Settings.
 */
val LocalAppDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Whether the app is rendering in dark theme (respects the in-app theme setting). */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun appDarkTheme(): Boolean = LocalAppDarkTheme.current

/**
 * True when the user has disabled system animations (accessibility "Remove animations" sets the
 * animator duration scale to 0). Decorative loops (shimmer, pulses, typing dots) must render a
 * static alternative when this is set.
 */
@Composable
fun reducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
fun TheFamilyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // statusBarColor/navigationBarColor are no-ops on API 35+ (edge-to-edge enforced)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
