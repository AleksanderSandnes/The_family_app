// Deep-link parsing and dispatch — familyapp://auth (PKCE callback),
// familyapp://chat/{conversationId} (push tap), familyapp://join?code= (invite).
import Foundation
import Observation

enum DeepLink: Equatable {
    case auth(URL)
    case chat(conversationId: String)
    case join(code: String)
    case wishlistShare(token: String)

    static func parse(_ url: URL) -> DeepLink? {
        guard url.scheme == SupabaseClientProvider.deepLinkScheme else { return nil }
        switch url.host() {
        case SupabaseClientProvider.deepLinkHost:
            return .auth(url)
        case "chat":
            let id = url.pathComponents.dropFirst().first ?? ""
            return id.isEmpty ? nil : .chat(conversationId: id)
        case "join":
            let code = queryValue(url, "code")
            return code.isEmpty ? nil : .join(code: code)
        case "wishlist":
            let token = queryValue(url, "token")
            return token.isEmpty ? nil : .wishlistShare(token: token)
        default:
            return nil
        }
    }

    private static func queryValue(_ url: URL, _ name: String) -> String {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == name })?
            .value ?? ""
    }
}

@Observable
@MainActor
final class DeepLinkRouter {
    /// Conversation to open once the chat tab is up (push tap / chat link).
    var pendingConversationId: String?
    /// Wishlist share token to redeem once the app shell is up. Lives here (not on the
    /// non-observable FamilyRepository) so MainTabView's onChange actually fires.
    var pendingWishlistShareToken: String?

    private let repo: FamilyRepositoryProtocol

    init(repo: FamilyRepositoryProtocol? = nil) {
        self.repo = repo ?? FamilyRepository.shared
    }

    func handle(_ url: URL) {
        switch DeepLink.parse(url) {
        case let .auth(url):
            // Feed the PKCE callback to supabase-swift; the auth flow observes
            // authStateChanges and finalizes the app session.
            let client = SupabaseClientProvider.client
            client.auth.handle(url)
        case let .chat(conversationId):
            pendingConversationId = conversationId
        case let .join(code):
            repo.setPendingJoinCode(code)
        case let .wishlistShare(token):
            pendingWishlistShareToken = token
        case nil:
            break
        }
    }
}
