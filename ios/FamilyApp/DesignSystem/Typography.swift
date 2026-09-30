// Type scale — SF Pro, sizes/weights 34/28/23/19/17/15/16/14/12.
// Every token scales with the user's Dynamic Type setting (DESIGN.md "The sp Rule"):
// base sizes are the design values at the default (.large) content size and scale up/down
// via UIFontMetrics anchored to the nearest system text style. Computed properties (not
// stored) so a Dynamic Type change re-resolves the fonts on the next render pass.
import SwiftUI
import UIKit

private func scaledFont(_ size: CGFloat, _ weight: Font.Weight, relativeTo style: UIFont.TextStyle) -> Font {
    Font.system(size: UIFontMetrics(forTextStyle: style).scaledValue(for: size), weight: weight)
}

extension Font {
    static var displaySmall: Font { scaledFont(34, .bold, relativeTo: .largeTitle) }
    static var headlineLarge: Font { scaledFont(28, .bold, relativeTo: .title1) }
    static var headlineMedium: Font { scaledFont(23, .bold, relativeTo: .title2) }
    static var headlineSmall: Font { scaledFont(19, .semibold, relativeTo: .title3) }
    static var titleLarge: Font { scaledFont(17, .semibold, relativeTo: .body) }
    static var titleMedium: Font { scaledFont(15, .semibold, relativeTo: .subheadline) }
    static var bodyLarge: Font { scaledFont(16, .regular, relativeTo: .callout) }
    static var bodyMedium: Font { scaledFont(14, .regular, relativeTo: .footnote) }
    static var labelLarge: Font { scaledFont(14, .semibold, relativeTo: .footnote) }
    static var labelMedium: Font { scaledFont(12, .medium, relativeTo: .caption1) }

    /// ── Liquid Glass ramp (1c) ────────────────────────────────────────────────
    /// Large tab-root title (Home/Chat/Calendar/Family/Profile headers). 24 / 700.
    static var tabTitle: Font { scaledFont(24, .bold, relativeTo: .title2) }
    /// Home greeting. 22 / 700.
    static var greeting: Font { scaledFont(22, .bold, relativeTo: .title2) }
    /// Pushed-screen inline nav title. 17 / 600.
    static var pushedTitle: Font { scaledFont(17, .semibold, relativeTo: .body) }
    /// Card / row title. 15 / 600.
    static var cardTitle: Font { scaledFont(15, .semibold, relativeTo: .subheadline) }
    /// Message / body text in glass. 15.5 / 400.
    static var message: Font { scaledFont(15.5, .regular, relativeTo: .body) }
    /// Subtitle / metadata caption. 12.5 / 400.
    static var caption: Font { scaledFont(12.5, .regular, relativeTo: .caption1) }
    /// UPPERCASE section header label. 12 / 700 (apply .tracking(0.6) + .uppercased()).
    static var sectionLabel: Font { scaledFont(12, .bold, relativeTo: .caption1) }
    /// Small feature eyebrow on summary cards. 10.5 / 700.
    static var eyebrow: Font { scaledFont(10.5, .bold, relativeTo: .caption2) }
}
