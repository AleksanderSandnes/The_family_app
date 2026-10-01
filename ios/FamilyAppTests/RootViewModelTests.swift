@testable import FamilyApp
import XCTest

@MainActor
final class RootViewModelTests: XCTestCase {
    private var repo: MockRepository!
    private var root: RootViewModel!
    private var continuation: AsyncStream<AuthSessionEvent>.Continuation!

    override func setUp() {
        super.setUp()
        repo = MockRepository()
        repo.authEvents = AsyncStream { self.continuation = $0 }
        root = RootViewModel(repo: repo)
    }

    override func tearDown() {
        continuation.finish()
        root = nil
        repo = nil
        continuation = nil
        super.tearDown()
    }

    private func waitUntil(_ predicate: () -> Bool) async throws {
        let deadline = ContinuousClock.now + .seconds(3)
        while !predicate() {
            guard ContinuousClock.now < deadline else {
                XCTFail("Auth transition did not finish")
                throw URLError(.timedOut)
            }
            try await Task.sleep(for: .milliseconds(1))
        }
    }

    func testCachedIdentityCannotOpenNavigationBeforeRestoration() {
        repo.session.signIn(userId: "stale-app")
        repo.session.setPermissionsRequested()
        XCTAssertEqual(root.gate, .loading)
    }

    func testMissingAndFailedRestorationCloseTheGate() async {
        for error in [RepositoryError.notAuthenticated as Error, URLError(.notConnectedToInternet)] {
            repo.session.signIn(userId: "stale-app")
            repo.session.setPermissionsRequested()
            repo.restoreAuthError = error
            let candidate = RootViewModel(repo: repo)
            await candidate.bootstrap()
            XCTAssertEqual(candidate.gate, .signedOut)
            XCTAssertNil(repo.session.currentUserId)
            await candidate.onSignedIn()
            XCTAssertFalse(repo.touchLastActiveCalled)
            XCTAssertFalse(repo.syncPushTokenCalled)
        }
    }

    func testRestorationReplacesTheCachedProfile() async {
        repo.session.signIn(userId: "wrong-app")
        repo.session.setPermissionsRequested()
        await root.bootstrap()
        XCTAssertEqual(root.gate, .signedIn)
        XCTAssertEqual(repo.session.currentUserId, "app-restored")
        XCTAssertEqual(repo.profileCalls, 1)
        XCTAssertTrue(repo.touchLastActiveCalled)
    }

    func testMissingProfileDoesNotUnlockMainNavigation() async {
        repo.session.signIn(userId: "stale-app")
        repo.profileError = RepositoryError.profileNotFound
        await root.bootstrap()
        XCTAssertEqual(root.gate, .signedOut)
        XCTAssertNil(repo.session.currentUserId)
        XCTAssertFalse(repo.touchLastActiveCalled)
    }

    func testPermissionsRemainRequiredAfterValidatedSignIn() async {
        await root.bootstrap()
        XCTAssertEqual(root.gate, .needsPermissions)
        await root.onSignedIn()
        XCTAssertFalse(repo.syncPushTokenCalled)
        root.completePermissionsOnboarding()
        XCTAssertEqual(root.gate, .signedIn)
    }

    func testSignOutClearsIdentityAndPreventsPushSync() async throws {
        repo.session.setPermissionsRequested()
        await root.bootstrap()
        repo.authUserID = nil
        XCTAssertEqual(root.gate, .signedOut)
        continuation.yield(.signedOut)
        try await waitUntil { self.repo.session.currentUserId == nil }
        XCTAssertNil(repo.session.currentUserId)
        await root.onSignedIn()
        XCTAssertFalse(repo.syncPushTokenCalled)
        XCTAssertTrue(repo.invalidatedCache)
    }

    func testRefreshKeepsVerifiedIdentity() async {
        repo.session.setPermissionsRequested()
        await root.bootstrap()
        repo.profileError = URLError(.notConnectedToInternet)
        root.receive(.authenticated(repo.restoredAuthUserID))
        XCTAssertEqual(root.gate, .signedIn)
        XCTAssertEqual(repo.profileCalls, 1)
    }

    func testCachedIdentityChangeCannotBorrowVerifiedSession() async {
        repo.session.setPermissionsRequested()
        await root.bootstrap()
        repo.session.signIn(userId: "different-app")
        XCTAssertEqual(root.gate, .signedOut)
        await root.onSignedIn()
        XCTAssertFalse(repo.syncPushTokenCalled)
    }

    func testAccountSwitchResolvesAndSyncsANewProfile() async throws {
        repo.session.setPermissionsRequested()
        await root.bootstrap()
        await root.onSignedIn()
        await root.onSignedIn()
        XCTAssertEqual(repo.pushSyncCalls, 1)
        repo.authUserID = "auth-b"
        repo.profileResult = "app-b"
        continuation.yield(.authenticated("auth-b"))
        try await waitUntil { self.repo.session.currentUserId == "app-b" }
        XCTAssertEqual(root.gate, .signedIn)
        XCTAssertEqual(repo.session.currentUserId, "app-b")
        await root.onSignedIn()
        XCTAssertEqual(repo.pushSyncCalls, 2)
        XCTAssertEqual(repo.preferenceSyncCalls, 2)
    }

    func testLateProfileCannotRestoreAnAccountAfterSignOut() async throws {
        var response: CheckedContinuation<String, Never>?
        repo.profileLookup = { await withCheckedContinuation { response = $0 } }
        let restore = Task { await self.root.bootstrap() }
        try await waitUntil { response != nil }
        repo.authUserID = nil
        continuation.yield(.signedOut)
        try await waitUntil { self.root.gate == .signedOut }
        response?.resume(returning: "stale-app")
        await restore.value
        XCTAssertEqual(root.gate, .signedOut)
        XCTAssertNil(repo.session.currentUserId)
        XCTAssertFalse(repo.touchLastActiveCalled)
    }
}
