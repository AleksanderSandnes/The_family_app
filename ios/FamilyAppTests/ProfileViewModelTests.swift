// Behaviour tests for ProfileViewModel via the injected MockRepository (no live backend).
@testable import FamilyApp
import XCTest

@MainActor
final class ProfileViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", name: String = "Alice") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.name = name
        user.email = "alice@test.com"
        user.mobile = "90012345"
        user.birthday = "1990-01-01"
        mock.users[userId] = user
        return mock
    }

    func testRefreshLoadsCurrentUser() async {
        let mock = makeMock()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }
        XCTAssertEqual(vm.user?.name, "Alice")
    }

    func testRefreshClearsUserWhenSignedOut() async {
        let mock = MockRepository() // no signIn
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { true } // let init's refresh Task run
        XCTAssertNil(vm.user)
    }

    func testSaveTrimsFieldsAndUpdatesLocalUser() async {
        let mock = makeMock()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { vm.user != nil }

        vm.save(name: "  Bob  ", email: " bob@test.com ", birthday: "1991-02-03", mobile: " 555 ")
        await waitUntil { !mock.updatedProfiles.isEmpty }

        let update = mock.updatedProfiles.first?.update
        XCTAssertEqual(mock.updatedProfiles.first?.userId, "u1")
        XCTAssertEqual(update?.name, "Bob")
        XCTAssertEqual(update?.email, "bob@test.com")
        XCTAssertEqual(update?.mobile, "555")
        // Optimistic local update reflects the new values immediately.
        XCTAssertEqual(vm.user?.name, "Bob")
        XCTAssertEqual(vm.user?.mobile, "555")
    }

    func testSaveIsNoOpWhenSignedOut() async {
        let mock = MockRepository()
        let vm = ProfileViewModel(repo: mock)
        await waitUntil { true }
        vm.save(name: "X", email: "x@x.com", birthday: "", mobile: "")
        await waitUntil { true }
        XCTAssertTrue(mock.updatedProfiles.isEmpty)
    }

    func testSignOutCallsRepository() async {
        let mock = makeMock()
        let vm = ProfileViewModel(repo: mock)
        vm.signOut()
        await waitUntil { mock.signOutCalled }
        XCTAssertTrue(mock.signOutCalled)
    }
}

/// Polls `condition` on the main actor until true or the timeout elapses — lets a
/// ViewModel's fire-and-forget `Task { }` work complete before assertions.
@MainActor
func waitUntil(timeout: TimeInterval = 2, _ condition: () -> Bool) async {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition(), Date() < deadline {
        await Task.yield()
    }
}
