// Feature-accent palette (Liquid Glass 1c). Feature colours live ONLY in icon badges
// and calendar event dots — never on large surfaces (the glass material does the talking).
// One entry per domain; each resolves an icon stroke, a translucent badge fill, and a
// calendar dot colour, all adapting light ↔ dark per the spec table.
import SwiftUI

enum FeatureAccent: CaseIterable {
    case shopping, meals, calendar, birthdays, wishlists, map, chat, family

    /// Icon-glyph stroke colour (darker in light, brighter in dark).
    var stroke: Color {
        switch self {
        case .shopping, .chat: Color(light: Color(hex: 0x4F55E6), dark: Color(hex: 0xA5ABFF))
        case .meals: Color(light: Color(hex: 0xD97706), dark: Color(hex: 0xFBBF24))
        case .calendar: Color(light: Color(hex: 0x0D9488), dark: Color(hex: 0x2DD4BF))
        case .birthdays: Color(light: Color(hex: 0xDB2777), dark: Color(hex: 0xF472B6))
        case .wishlists, .family: Color(light: Color(hex: 0x7C3AED), dark: Color(hex: 0xC4B5FD))
        case .map: Color(light: Color(hex: 0x059669), dark: Color(hex: 0x34D399))
        }
    }

    /// Translucent badge fill (12–15% in light, 16% in dark).
    var badgeFill: Color {
        switch self {
        case .shopping, .chat: Color(
                light: Color(hex: 0x6366F1).opacity(0.14),
                dark: Color(hex: 0xA5ABFF).opacity(0.16)
            )
        case .meals: Color(light: Color(hex: 0xF59E0B).opacity(0.15), dark: Color(hex: 0xFBBF24).opacity(0.16))
        case .calendar: Color(light: Color(hex: 0x14B8A6).opacity(0.14), dark: Color(hex: 0x2DD4BF).opacity(0.16))
        case .birthdays: Color(light: Color(hex: 0xEC4899).opacity(0.13), dark: Color(hex: 0xF472B6).opacity(0.16))
        case .wishlists, .family: Color(
                light: Color(hex: 0x8B5CF6).opacity(0.14),
                dark: Color(hex: 0xC4B5FD).opacity(0.16)
            )
        case .map: Color(light: Color(hex: 0x10B981).opacity(0.14), dark: Color(hex: 0x34D399).opacity(0.16))
        }
    }

    /// Calendar event-dot colour (fixed, saturated).
    var dot: Color {
        switch self {
        case .shopping, .chat: Color(hex: 0x4F55E6)
        case .meals: Color(hex: 0xF59E0B)
        case .calendar: Color(hex: 0x14B8A6)
        case .birthdays: Color(hex: 0xEC4899)
        case .wishlists, .family: Color(hex: 0x8B5CF6)
        case .map: Color(hex: 0x10B981)
        }
    }
}
