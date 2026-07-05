// Typed routes — the iOS twin of Routes.kt. Pushed onto per-tab NavigationStacks.
import Foundation

enum Route: Hashable {
    // Detail/feature screens (Android: horizontal-slide destinations)
    case shopping
    case shoppingDetail(listId: String)
    case meal
    case mealDetail(planId: String)
    case birthday
    case wishlist
    case wishlistDetail(wishlistId: String)
    case chatDetail(conversationId: String)
    case profileEdit
    case settings
    case familyMap
}

/// Bottom tabs (Android: crossfade destinations).
enum Tab: Hashable {
    case home
    case calendar
    case chat
    case family
    case profile
}

enum DeepLinkURL {
    static func invite(code: String) -> URL {
        guard let url = URL(string: "familyapp://join?code=\(code)") else {
            preconditionFailure("Invalid invite deep-link URL for code: \(code)")
        }
        return url
    }

    static func chat(conversationId: String) -> URL {
        guard let url = URL(string: "familyapp://chat/\(conversationId)") else {
            preconditionFailure("Invalid chat deep-link URL for conversation: \(conversationId)")
        }
        return url
    }
}
