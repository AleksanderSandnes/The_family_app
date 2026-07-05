// Spacing / radius / elevation / motion tokens — mirrors Spacing.kt (4-pt grid).
// Screens must reference these instead of hardcoding values.
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

/// Corner-radius tokens — mirrors Radius + AppShapes (10/14/20/28/36).
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
}

/// Two-level elevation scale (shadow radii for resting surfaces and raised elements).
enum Elevation {
    static let resting: CGFloat = 2
    static let raised: CGFloat = 6
}

/// Motion duration tokens (seconds — SwiftUI convention).
enum Motion {
    static let slide: Double = 0.30
    static let fade: Double = 0.20
}
