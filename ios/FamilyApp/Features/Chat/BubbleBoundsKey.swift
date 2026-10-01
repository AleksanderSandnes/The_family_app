import SwiftUI

// MARK: - Reaction anchor

/// Reports the bounds of the bubble currently targeted for a reaction, so the
/// screen-level overlay can position the reaction bar above it. Internal (not private)
/// so MessageRow can set the preference.
struct BubbleBoundsKey: PreferenceKey {
    static let defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, new in new }
    }
}
