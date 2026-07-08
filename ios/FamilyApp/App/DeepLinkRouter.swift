// Deep-link parsing and dispatch — familyapp://auth (PKCE callback),
// familyapp://chat/{conversationId} (push tap), familyapp://join?code= (invite).
import Foundation
import Observation

enum DeepLink: Equatable {
    case auth(URL)
    case chat(conversationId: String)
    case join(code: String)

    static func parse(_ url: URL) -> DeepLink? {
        guard url.scheme == SupabaseClientProvider.deepLinkScheme else { return nil }
        switch url.host() {
        case SupabaseClientProvider.deepLinkHost:
            return .auth(url)
        case "chat":
            let id = url.pathComponents.dropFirst().first ?? ""
            return id.isEmpty ? nil : .chat(conversationId: id)
        case "join":
            let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?
                .first(where: { $0.name == "code" })?
                .value ?? ""
            return code.isEmpty ? nil : .join(code: code)
        default:
            return nil
        }
    }
}

@Observable
@MainActor
final class DeepLinkRouter {
    /// Conversation to open once the chat tab is up (push tap / chat link).
    var pendingConversationId: String?

    private let repo: FamilyRepositoryProtocol

    init(repo: FamilyRepositoryProtocol = FamilyRepository.shared) {
        self.repo = repo
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
        case nil:
            break
        }
    }
}
