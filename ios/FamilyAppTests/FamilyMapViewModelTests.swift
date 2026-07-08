// Behaviour tests for FamilyMapViewModel via MockRepository + NoopRealtimeObserver.
// Only the repo-facing behaviour is exercised (CLLocationManager isn't driven directly;
// the delegate callback is invoked to simulate a location fix).
import CoreLocation
@testable import FamilyApp
import XCTest

@MainActor
final class FamilyMapViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.name = "Alice"
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> FamilyMapViewModel {
        FamilyMapViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    private func location(userId: String, familyId: String = "f1") -> UserLocationModel {
        var loc = UserLocationModel()
        loc.userId = userId
        loc.familyId = familyId
        loc.visible = true
        return loc
    }

    // MARK: - Load

    func testLoadLocationsFiltersOutSelf() async {
        let mock = makeMock()
        mock.userLocationsResult = [location(userId: "u1"), location(userId: "u2")]
        let vm = makeVM(mock)
        await waitUntil { vm.locations.contains { $0.userId == "u2" } }
        XCTAssertTrue(vm.locations.contains { $0.userId == "u2" })
        XCTAssertFalse(vm.locations.contains { $0.userId == "u1" })
    }

    // MARK: - Publish (via the delegate's first-fix path)

    func testPublishLocationOnFixCallsRepo() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        let manager = CLLocationManager()
        vm.locationManager(manager, didUpdateLocations: [CLLocation(latitude: 59.9, longitude: 10.7)])
        await waitUntil { !mock.upsertedLocations.isEmpty }
        let published = mock.upsertedLocations.first
        XCTAssertEqual(published?.userId, "u1")
        XCTAssertEqual(published?.displayName, "Alice")
        XCTAssertEqual(published?.lat ?? 0, 59.9, accuracy: 0.0001)
        XCTAssertEqual(published?.lng ?? 0, 10.7, accuracy: 0.0001)
    }

    // MARK: - Clear

    func testClearOwnLocationCallsRepoWhenNotPersistent() async {
        let mock = makeMock() // locationVisible defaults to false
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.clearOwnLocation()
        await waitUntil { !mock.clearedLocationUserIds.isEmpty }
        XCTAssertEqual(mock.clearedLocationUserIds.first, "u1")
    }

    func testClearOwnLocationSkipsWhenPersistentSharingOn() async {
        let mock = makeMock()
        mock.session.setLocationVisible(true)
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.clearOwnLocation()
        await waitUntil { true }
        XCTAssertTrue(mock.clearedLocationUserIds.isEmpty)
    }
}
