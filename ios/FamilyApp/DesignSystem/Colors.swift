// Design tokens — the iOS twin of ui/theme/Color.kt + Theme.kt. Exact same hex values.
// Semantic tokens adapt to light/dark automatically (the ThemeMode override is applied
// via .preferredColorScheme at the root, and dynamic colors follow it).
import SwiftUI
import UIKit

/// A user-picked colour stored as an 0xRRGGBB int → SwiftUI Color (nil passes through).
func hexColor(_ value: Int?) -> Color? {
    value.map { Color(hex: UInt32(truncatingIfNeeded: $0)) }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }

    /// ARGB int from the DB `avatar_color` column (Android Color int).
    init(argb: Int) {
        let value = UInt32(bitPattern: Int32(truncatingIfNeeded: argb))
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: Double((value >> 24) & 0xFF) / 255
        )
    }

    /// Adaptive color that resolves per the effective interface style.
    init(light: Color, dark: Color) {
        self.init(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(dark) : UIColor(light)
        })
    }
}

/// Raw palette — mirrors Color.kt exactly.
enum Palette {
    // Brand — refined indigo / violet
    static let indigo50 = Color(hex: 0xEEF0FF)
    static let indigo100 = Color(hex: 0xE0E3FF)
    static let indigo200 = Color(hex: 0xC4C9FF)
    static let indigo300 = Color(hex: 0x9CA3FF)
    static let indigo500 = Color(hex: 0x6366F1)
    static let indigo600 = Color(hex: 0x5457E8)
    static let indigo700 = Color(hex: 0x4338CA)
    static let violet500 = Color(hex: 0x8B5CF6)
    static let violet600 = Color(hex: 0x7C3AED)

    // Accent
    static let pink500 = Color(hex: 0xEC4899)
    static let teal500 = Color(hex: 0x14B8A6)
    static let amber500 = Color(hex: 0xF59E0B)
    static let emerald500 = Color(hex: 0x10B981)
    static let rose500 = Color(hex: 0xF43F5E)

    // Neutrals (light)
    static let slate900 = Color(hex: 0x0B1020)
    static let slate800 = Color(hex: 0x1E293B)
    static let slate700 = Color(hex: 0x334155)
    static let slate600 = Color(hex: 0x475569)
    static let slate500 = Color(hex: 0x64748B)
    static let slate400 = Color(hex: 0x94A3B8)
    static let slate200 = Color(hex: 0xE2E8F0)
    static let slate100 = Color(hex: 0xF1F5F9)
    static let canvas = Color(hex: 0xF7F8FC)
    static let surfaceLight = Color(hex: 0xFFFFFF)

    // Neutrals (dark)
    static let ink = Color(hex: 0x0A0E1A)
    static let inkSurface = Color(hex: 0x141A2A)
    static let inkSurfaceVariant = Color(hex: 0x1E2638)
    static let inkBorder = Color(hex: 0x2A3349)
    static let inkText = Color(hex: 0xE8EBF5)
    static let inkTextMuted = Color(hex: 0x9AA4BE)

    // Secondary containers (Theme.kt inline values)
    static let violetContainerLight = Color(hex: 0xEDE4FF)
    static let violetContainerDark = Color(hex: 0x4C1D95)

    // Hero gradient dark endpoints (Color.kt private values)
    static let heroDarkStart = Color(hex: 0x3730A3)
    static let heroDarkEnd = Color(hex: 0x6D28D9)

    // ── Liquid Glass (direction 1c) ──────────────────────────────────────────
    // Single accent — quieted indigo. Used for all interactive elements.
    static let accent = Color(hex: 0x4F55E6)
    static let accentDark = Color(hex: 0xA5ABFF) // brightened accent for dark text/icons
    static let accentActiveTab = Color(hex: 0xC9CDFF) // active tab glyph in dark

    // Ambient canvas bases (the three radial washes render over these)
    static let ambientBaseLight = Color(hex: 0xEFF1F8)
    static let ambientBaseDark = Color(hex: 0x0B0D16)

    // Text ramp
    static let inkLight = Color(hex: 0x16192A)
    static let inkDark = Color(hex: 0xECEEF8)
    static let secondaryLight = Color(hex: 0x5F6780)
    static let secondaryDark = Color(hex: 0x98A0BC)
    static let captionLight = Color(hex: 0x8B92AC)
    static let captionDark = Color(hex: 0x6A7290)

    // Status
    static let destructive = Color(hex: 0xE11D48) // menu rows, leave/sign-out, delete
    static let liveGreen = Color(hex: 0x10B981) // today / live / done
    static let liveGreenText = Color(hex: 0x059669) // "All done" text
    static let weekAmberText = Color(hex: 0xB45309) // "this week" urgency text
    static let staleDot = Color(hex: 0xCBD2E0) // map stale dot
    static let mapBase = Color(hex: 0xE7ECE3) // family-map flat base
}

/// Semantic scheme — mirrors LightColors/DarkColors in Theme.kt. Use these at call
/// sites, never raw palette values (same rule as MaterialTheme.colorScheme.*).
extension Color {
    // Accent — the single interactive colour (quieted indigo). Brightens in dark.
    static let appPrimary = Color(light: Palette.accent, dark: Palette.accentDark)
    static let appOnPrimary = Color(light: .white, dark: Palette.ink)
    static let appPrimaryContainer = Color(light: Palette.indigo100, dark: Palette.indigo700)
    static let appOnPrimaryContainer = Color(light: Palette.indigo700, dark: Palette.indigo100)
    static let appSecondary = Color(light: Palette.violet600, dark: Palette.violet500)
    static let appOnSecondary = Color(light: .white, dark: Palette.ink)
    static let appSecondaryContainer = Color(
        light: Palette.violetContainerLight, dark: Palette.violetContainerDark
    )
    static let appOnSecondaryContainer = Color(
        light: Palette.violet600, dark: Palette.violetContainerLight
    )
    static let appTertiary = Palette.pink500
    // Ambient canvas base — the radial washes are drawn on top by `.ambientBackground()`.
    static let appBackground = Color(light: Palette.ambientBaseLight, dark: Palette.ambientBaseDark)
    static let appOnBackground = Color(light: Palette.inkLight, dark: Palette.inkDark)
    static let appSurface = Color(light: Palette.surfaceLight, dark: Palette.inkSurface)
    static let appOnSurface = Color(light: Palette.inkLight, dark: Palette.inkDark)
    static let appSurfaceVariant = Color(light: Palette.slate100, dark: Palette.inkSurfaceVariant)
    /// Secondary text — spec ramp (#5F6780 / #98A0BC).
    static let appOnSurfaceVariant = Color(light: Palette.secondaryLight, dark: Palette.secondaryDark)
    // Caption / tertiary text (#8B92AC / #6A7290).
    static let appCaption = Color(light: Palette.captionLight, dark: Palette.captionDark)
    static let appOutline = Color(light: Palette.slate200, dark: Palette.inkBorder)
    static let appOutlineVariant = Color(light: Palette.slate100, dark: Palette.inkSurfaceVariant)
    static let appError = Palette.destructive

    // Semantic status roles
    static let appSuccess = Palette.liveGreen
    static let appWarning = Palette.amber500
    static let appDanger = Palette.destructive
}
