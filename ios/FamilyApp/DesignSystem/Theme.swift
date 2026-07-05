// Theme override — mirrors ThemeMode in SessionManager.kt / TheFamilyAppTheme.
// Applied at the root with .preferredColorScheme; all adaptive Colors follow it.
import SwiftUI

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}
