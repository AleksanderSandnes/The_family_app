// Type scale — mirrors Type.kt (SF Pro replaces the Android sans-serif; same sizes
// and weights: 34/28/23/19/17/15/16/14/12).
import SwiftUI

extension Font {
    static let displaySmall = Font.system(size: 34, weight: .bold)
    static let headlineLarge = Font.system(size: 28, weight: .bold)
    static let headlineMedium = Font.system(size: 23, weight: .bold)
    static let headlineSmall = Font.system(size: 19, weight: .semibold)
    static let titleLarge = Font.system(size: 17, weight: .semibold)
    static let titleMedium = Font.system(size: 15, weight: .semibold)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    static let labelLarge = Font.system(size: 14, weight: .semibold)
    static let labelMedium = Font.system(size: 12, weight: .medium)

    /// ── Liquid Glass ramp (1c) ────────────────────────────────────────────────
    /// Large tab-root title (Home/Chat/Calendar/Family/Profile headers). 24 / 700.
    static let tabTitle = Font.system(size: 24, weight: .bold)
    /// Home greeting. 22 / 700.
    static let greeting = Font.system(size: 22, weight: .bold)
    /// Pushed-screen inline nav title. 17 / 600.
    static let pushedTitle = Font.system(size: 17, weight: .semibold)
    /// Card / row title. 15 / 600.
    static let cardTitle = Font.system(size: 15, weight: .semibold)
    /// Message / body text in glass. 15.5 / 400.
    static let message = Font.system(size: 15.5, weight: .regular)
    /// Subtitle / metadata caption. 12.5 / 400.
    static let caption = Font.system(size: 12.5, weight: .regular)
    /// UPPERCASE section header label. 12 / 700 (apply .tracking(0.6) + .uppercased()).
    static let sectionLabel = Font.system(size: 12, weight: .bold)
    /// Small feature eyebrow on summary cards. 10.5 / 700.
    static let eyebrow = Font.system(size: 10.5, weight: .bold)
}
