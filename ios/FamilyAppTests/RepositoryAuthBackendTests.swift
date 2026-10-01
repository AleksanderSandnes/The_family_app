@testable import FamilyApp

// FamilyRepository auth flows and StorageService paths against the stubbed Supabase
// transport (real GoTrue request/response round-trips with a fictional account).
import XCTest

@MainActor
final class RepositoryAuthBackendTests: XCTestCase {
    private let repo = FamilyRepository.shared
    private let authID = StubSupabase.authUserID

    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
        SessionStore.shared.signOut()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - Sign-in flows

    func testLoginNormalisesEmailAndResolvesTheAppUser() async throws {
        StubSupabase.serveAuth(appUserID: "user-emma")

        let userID = try await repo.login(email: "  Emma@Example.com ", password: "fictional-pass")

        XCTAssertEqual(userID, "user-emma")
        XCTAssertEqual(SessionStore.shared.currentUserId, "user-emma")
        XCTAssertTrue(repo.hasAuthSession())
        XCTAssertEqual(repo.currentAuthUserID(), authID)
        let token = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/token").first)
        XCTAssertEqual(token.jsonObject["email"] as? String, "emma@example.com")
        let lookup = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/users").first)
        XCTAssertTrue(lookup.hasFilter("auth_id", "eq.\(authID)"))
        let restored = try await repo.restoreAuthSession()
        XCTAssertEqual(restored, authID)
    }

    func testResolvingWithoutAProfileThrows() async throws {
        StubSupabase.serveAuth()
        StubTransport.respond(table: "users", with: [UserModel]())
        do {
            _ = try await repo.login(email: "emma@example.com", password: "fictional-pass")
            XCTFail("Expected profileNotFound")
        } catch {
            XCTAssertEqual(error as? RepositoryError, .profileNotFound)
        }
    }

    func testResolvingWithoutASessionThrows() async {
        do {
            _ = try await repo.resolveAuthenticatedAppUserID()
            XCTFail("Expected notAuthenticated")
        } catch {
            XCTAssertEqual(error as? RepositoryError, .notAuthenticated)
        }
        XCTAssertFalse(repo.hasAuthSession())
        XCTAssertNil(repo.currentAuthUserID())
    }

    func testSignupCodeConfirmationAndResend() async throws {
        StubSupabase.serveAuth(appUserID: "user-nora")
        let userID = try await repo.confirmSignupEmail(email: " Nora@Example.com", code: "123456")
        try await repo.resendSignupCode(email: "Nora@Example.com ")

        XCTAssertEqual(userID, "user-nora")
        let verify = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/verify").first)
        XCTAssertEqual(verify.jsonObject["email"] as? String, "nora@example.com")
        XCTAssertEqual(verify.jsonObject["type"] as? String, "signup")
        let resend = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/resend").first)
        XCTAssertEqual(resend.jsonObject["email"] as? String, "nora@example.com")
    }

    func testPasswordResetRequestAndConfirmation() async throws {
        StubSupabase.serveAuth(appUserID: "user-lars")
        try await repo.sendPasswordResetEmail(email: " Lars@Example.com ")
        let userID = try await repo.confirmPasswordReset(
            email: "lars@example.com",
            code: "654321",
            newPassword: "new-fictional-pass"
        )

        XCTAssertEqual(userID, "user-lars")
        let recover = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/recover").first)
        XCTAssertEqual(recover.jsonObject["email"] as? String, "lars@example.com")
        let verify = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/verify").first)
        XCTAssertEqual(verify.jsonObject["type"] as? String, "recovery")
        let update = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/user", method: "PUT").first)
        XCTAssertEqual(update.jsonObject["password"] as? String, "new-fictional-pass")
    }

    func testRegisterSendsProfileMetadata() async throws {
        StubTransport.respond("POST", "/auth/v1/signup", body: StubJSON.literal([
            "id": authID,
            "aud": "authenticated",
            "role": "authenticated",
            "email": "jonas@example.com",
            "app_metadata": ["provider": "email"],
            "user_metadata": [String: Any](),
            "created_at": "2026-01-01T00:00:00Z",
            "updated_at": "2026-01-01T00:00:00Z",
        ]))

        try await repo.register(
            name: " Jonas Nordmann ",
            email: "Jonas@Example.com",
            password: "fictional-pass",
            birthday: "2017-11-30",
            mobile: "+47 400 00 001"
        )

        let signup = try XCTUnwrap(StubTransport.requests(to: "/auth/v1/signup").first)
        XCTAssertEqual(signup.jsonObject["email"] as? String, "jonas@example.com")
        let metadata = try XCTUnwrap(signup.jsonObject["data"] as? [String: Any])
        XCTAssertEqual(metadata["full_name"] as? String, "Jonas Nordmann")
        XCTAssertEqual(metadata["birthday"] as? String, "2017-11-30")
        XCTAssertEqual(metadata["avatar_color"] as? Int, FamilyRepository.palette(" Jonas Nordmann "))
    }

    // MARK: - Sign-out and deletion

    func testSignOutClearsAuthAndAppSession() async throws {
        try await StubSupabase.signIn()
        await repo.signOut()
        XCTAssertFalse(repo.hasAuthSession())
        XCTAssertNil(SessionStore.shared.currentUserId)
        XCTAssertFalse(StubTransport.requests(to: "/auth/v1/logout").isEmpty)
    }

    func testDeleteAccountCallsTheEdgeFunctionThenClearsLocalState() async throws {
        try await StubSupabase.signIn()
        try await repo.deleteAccount()
        let call = try XCTUnwrap(StubTransport.requests(to: "/functions/v1/delete-account").first)
        XCTAssertEqual(call.jsonObject["confirm"] as? String, "DELETE_MY_ACCOUNT")
        XCTAssertFalse(repo.hasAuthSession())
        XCTAssertNil(SessionStore.shared.currentUserId)
    }

    func testDeleteAccountFailureKeepsTheSession() async throws {
        try await StubSupabase.signIn()
        StubTransport.respond("POST", "/functions/v1/delete-account", status: 500, body: Data("{}".utf8))
        do {
            try await repo.deleteAccount()
            XCTFail("Expected the function error to surface")
        } catch {
            XCTAssertTrue(repo.hasAuthSession())
            XCTAssertEqual(SessionStore.shared.currentUserId, "user-emma")
        }
    }

    // MARK: - Auth event streams

    func testAuthEventStreamsReportSignIn() async throws {
        let sessionEvents = repo.authSessionEvents()
        let signedInEvents = repo.authSignedInEvents()
        let authenticated = Task { @MainActor () -> AuthSessionEvent? in
            for await event in sessionEvents where event != .signedOut {
                return event
            }
            return nil
        }
        let signedIn = Task { @MainActor () -> Bool in
            for await _ in signedInEvents {
                return true
            }
            return false
        }

        try await StubSupabase.signIn()

        let event = await value(of: authenticated)
        let sawSignedIn = await value(of: signedIn)
        XCTAssertEqual(event, .authenticated(authID))
        XCTAssertEqual(sawSignedIn, true)
    }

    func testAuthSessionEventMapping() {
        XCTAssertEqual(AuthSessionEvent.from(.signedOut, authUserID: "a"), .signedOut)
        XCTAssertEqual(AuthSessionEvent.from(.initialSession, authUserID: nil), .signedOut)
        XCTAssertNil(AuthSessionEvent.from(.initialSession, authUserID: "a"))
        XCTAssertEqual(AuthSessionEvent.from(.tokenRefreshed, authUserID: "a"), .authenticated("a"))
        XCTAssertNil(AuthSessionEvent.from(.signedIn, authUserID: nil))
    }

    // MARK: - StorageService paths

    func testStorageUploadsUseTheAuthUidPathRules() async throws {
        try await StubSupabase.signIn()
        let pixels = Data("pixels".utf8)

        let avatar = try await StorageService.uploadAvatar(data: pixels, filename: "avatar.jpg")
        try await StorageService.deleteAvatar(filename: "avatar.jpg")
        let group = try await StorageService.uploadGroupImage(data: pixels, filename: "group.jpg")
        let wish = try await StorageService.uploadWishImage(data: pixels, appUserId: "user-emma", filename: "w.jpg")
        let media = try await StorageService.uploadChatMedia(conversationId: "chat-1", data: pixels, filename: "v.m4a")

        XCTAssertTrue(avatar.hasSuffix("/storage/v1/object/public/avatars/\(authID)/avatar.jpg"))
        XCTAssertTrue(group.hasSuffix("/group-images/\(authID)/group.jpg"))
        XCTAssertTrue(wish.hasSuffix("/wish-images/user-emma/w.jpg"))
        XCTAssertTrue(media.hasSuffix("/chat-media/chat-1/\(authID)/v.m4a"))
        XCTAssertFalse(StubTransport.requests(to: "/storage/v1/object/avatars", method: "DELETE").isEmpty)
    }

    func testStorageRequiresAnAuthSession() async {
        do {
            _ = try await StorageService.uploadAvatar(data: Data(), filename: "avatar.jpg")
            XCTFail("Expected notAuthenticated")
        } catch {
            XCTAssertEqual(error as? RepositoryError, .notAuthenticated)
        }
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }

    /// Awaits a task but gives up after a few seconds so a missing event fails instead of hanging.
    private func value<T: Sendable>(of task: Task<T?, Never>) async -> T? {
        let timeout = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            task.cancel()
        }
        defer { timeout.cancel() }
        return await task.value
    }

    private func value(of task: Task<Bool, Never>) async -> Bool? {
        let timeout = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            task.cancel()
        }
        defer { timeout.cancel() }
        return await task.value
    }
}
