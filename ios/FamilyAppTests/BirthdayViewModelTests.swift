// Behaviour tests for BirthdayViewModel via MockRepository + NoopRealtimeObserver.
@testable import FamilyApp
import XCTest

@MainActor
final class BirthdayViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> BirthdayViewModel {
        BirthdayViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    func testRefreshLoadsBirthdaysFromRepo() async {
        let mock = makeMock()
        var b = BirthdayModel()
        b.id = "b1"; b.name = "Alice"; b.familyId = "f1"
        mock.birthdaysResult = [b]
        let vm = makeVM(mock)
        // Wait for the reload result specifically (the VM seeds from a shared static cache).
        await waitUntil { vm.birthdays.contains { $0.name == "Alice" } }
        XCTAssertTrue(vm.birthdays.contains { $0.name == "Alice" })
    }

    func testAddInsertsWithCorrectFieldsAndOptimisticAppend() async {
        let mock = makeMock()
        mock.birthdaysResult = []
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.add(name: "Bob", date: "1999-01-08", icon: "cake", color: 0x6366F1)
        await waitUntil { !mock.insertedBirthdays.isEmpty }

        let inserted = mock.insertedBirthdays.first
        XCTAssertEqual(inserted?.name, "Bob")
        XCTAssertEqual(inserted?.madeByUserId, "u1")
        XCTAssertEqual(inserted?.familyId, "f1")
        XCTAssertEqual(inserted?.icon, "cake")
        XCTAssertEqual(inserted?.color, 0x6366F1)
    }

    func testUpdateCallsRepositoryWithNewValues() async {
        let mock = makeMock()
        var b = BirthdayModel()
        b.id = "b1"; b.name = "Old"
        mock.birthdaysResult = [b]
        let vm = makeVM(mock)
        await waitUntil { !vm.birthdays.isEmpty }

        vm.update(id: "b1", name: "New", date: "2000-01-01", icon: "star", color: nil)
        await waitUntil { !mock.updatedBirthdays.isEmpty }

        XCTAssertEqual(mock.updatedBirthdays.first?.id, "b1")
        XCTAssertEqual(mock.updatedBirthdays.first?.name, "New")
        XCTAssertEqual(mock.updatedBirthdays.first?.icon, "star")
        XCTAssertNil(mock.updatedBirthdays.first?.color)
    }

    func testDeleteRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var b = BirthdayModel()
        b.id = "b1"
        mock.birthdaysResult = [b]
        let vm = makeVM(mock)
        await waitUntil { !vm.birthdays.isEmpty }

        vm.delete(b)
        await waitUntil { !mock.deletedBirthdayIds.isEmpty }
        XCTAssertEqual(mock.deletedBirthdayIds.first, "b1")
    }

    func testCurrentUserIdReadsFromSession() {
        let mock = makeMock(userId: "u9")
        let vm = makeVM(mock)
        XCTAssertEqual(vm.currentUserId, "u9")
    }
}
