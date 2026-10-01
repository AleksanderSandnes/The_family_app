import Foundation

struct AuthIdentitySnapshot: Equatable {
    let authUserID: String
    let accessToken: String
}

enum AuthSessionEvent: Equatable {
    case authenticated(String)
    case signedOut
}

/// A profile response must still belong to the session that requested it.
@MainActor
struct AuthProfileResolver {
    let currentIdentity: () -> AuthIdentitySnapshot?
    let loadAppUserID: (String) async throws -> String

    func resolve() async throws -> String {
        guard let identity = currentIdentity() else { throw RepositoryError.notAuthenticated }
        let userID = try await loadAppUserID(identity.authUserID)
        try Task.checkCancellation()
        guard currentIdentity() == identity else { throw RepositoryError.notAuthenticated }
        return userID
    }
}
