@testable import FamilyApp
import Supabase
import XCTest

@MainActor
final class AuthProfileResolverTests: XCTestCase {
    private let original = AuthIdentitySnapshot(authUserID: "auth-a", accessToken: "fictional-token-a")

    func testLocalInitialSessionCannotAuthorizeNavigation() {
        XCTAssertNil(AuthSessionEvent.from(.initialSession, authUserID: "expired-local-account"))
        XCTAssertEqual(AuthSessionEvent.from(.initialSession, authUserID: nil), .signedOut)
    }

    func testConfirmedEventsAndSignOutAreMapped() {
        for event in [AuthChangeEvent.signedIn, .tokenRefreshed, .passwordRecovery, .userUpdated] {
            XCTAssertEqual(AuthSessionEvent.from(event, authUserID: "auth-a"), .authenticated("auth-a"))
        }
        XCTAssertEqual(AuthSessionEvent.from(.signedOut, authUserID: "old-account"), .signedOut)
        XCTAssertNil(AuthSessionEvent.from(.signedIn, authUserID: nil))
    }

    func testResolvesTheProfileForTheAuthenticatedIdentity() async throws {
        let resolver = AuthProfileResolver(currentIdentity: { self.original }, loadAppUserID: { authID in
            XCTAssertEqual(authID, "auth-a")
            return "app-a"
        })
        let result = try await resolver.resolve()
        XCTAssertEqual(result, "app-a")
    }

    func testMissingSessionNeverQueriesAProfile() async {
        let resolver = AuthProfileResolver(currentIdentity: { nil }, loadAppUserID: { _ in
            XCTFail("Profile queried without a session")
            return "stale"
        })
        do {
            _ = try await resolver.resolve()
            XCTFail("Missing session accepted")
        } catch { XCTAssertTrue(error is RepositoryError) }
    }

    func testLateResponsesAfterAuthChangesAreRejected() async {
        let replacements: [AuthIdentitySnapshot?] = [
            nil,
            AuthIdentitySnapshot(authUserID: "auth-b", accessToken: "fictional-token-b"),
            AuthIdentitySnapshot(authUserID: "auth-a", accessToken: "fictional-new-session"),
        ]
        for replacement in replacements {
            var identity: AuthIdentitySnapshot? = original
            let resolver = AuthProfileResolver(currentIdentity: { identity }, loadAppUserID: { _ in
                identity = replacement
                return "app-a"
            })
            do {
                _ = try await resolver.resolve()
                XCTFail("Late profile accepted after identity changed")
            } catch { XCTAssertTrue(error is RepositoryError) }
        }
    }

    func testProfileFailureIsNotReplacedByCachedIdentity() async {
        let resolver = AuthProfileResolver(currentIdentity: { self.original }, loadAppUserID: { _ in
            throw RepositoryError.profileNotFound
        })
        do {
            _ = try await resolver.resolve()
            XCTFail("Failed profile resolved")
        } catch { XCTAssertTrue(error is RepositoryError) }
    }

    func testCancellationRejectsACompletedProfileResponse() async {
        let resolver = AuthProfileResolver(currentIdentity: { self.original }, loadAppUserID: { _ in "app-a" })
        let task = Task { try await resolver.resolve() }
        task.cancel()
        do {
            _ = try await task.value
            XCTFail("Cancelled profile returned")
        } catch { XCTAssertTrue(error is CancellationError) }
    }
}
