// Spacing / radius / elevation / motion tokens — 4-pt grid. Reference these, don't hardcode.
import SwiftUI

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32

    /// Canonical layout values used app-wide.
    static let screenEdge: CGFloat = 20
    static let cardPadding: CGFloat = 18
    static let cardGap: CGFloat = 12
}

/// Corner-radius tokens (10/14/20/28/36).
enum Radius {
    static let extraSmall: CGFloat = 10
    static let small: CGFloat = 14
    static let medium: CGFloat = 20
    static let large: CGFloat = 28
    static let extraLarge: CGFloat = 36

    static let card: CGFloat = 20
    static let field: CGFloat = 16
    static let button: CGFloat = 18
    static let sheet: CGFloat = 28

    // ── Liquid Glass scale (1c) ───────────────────────────────────────────────
    static let badge: CGFloat = 12 // icon badges (12–14)
    static let badgeLarge: CGFloat = 14
    static let row: CGFloat = 20 // list rows / grid tiles / bubbles
    static let overviewCard: CGFloat = 22 // overview cards
    static let bigCard: CGFloat = 26 // family card, month grid, hero, meal-week header
    static let tabBar: CGFloat = 33 // floating tab bar
    static let fab: CGFloat = 26 // extended FAB (h52)
    static let segment: CGFloat = 14 // segmented control track
    static let segmentThumb: CGFloat = 11
    static let menu: CGFloat = 18 // ⋯ popover menus
}

/// Two-level elevation scale (shadow radii for resting surfaces and raised elements).
enum Elevation {
    static let resting: CGFloat = 2
    static let raised: CGFloat = 6
}

/// Motion duration tokens (seconds — SwiftUI convention).
enum Motion {
    static let slide = 0.30
    static let fade = 0.20
}
