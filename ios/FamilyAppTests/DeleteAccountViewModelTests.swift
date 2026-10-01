// Behaviour tests for DeleteAccountViewModel via the injected MockRepository (no live backend).
@testable import FamilyApp
import XCTest

@MainActor
final class DeleteAccountViewModelTests: XCTestCase {
    private struct DeletionFailed: Error {}

    func testSuccessfulDeletionCallsRepositoryOnceAndReportsNoFailure() async {
        let mock = MockRepository()
        let vm = DeleteAccountViewModel(repo: mock)
        vm.deleteAccount()
        XCTAssertTrue(vm.isDeleting)
        await waitUntil { !vm.isDeleting }
        XCTAssertEqual(mock.deleteAccountCalls, 1)
        XCTAssertFalse(vm.failed)
    }

    func testFailedDeletionSetsFailedAndAllowsRetry() async {
        let mock = MockRepository()
        mock.deleteAccountError = DeletionFailed()
        let vm = DeleteAccountViewModel(repo: mock)
        vm.deleteAccount()
        await waitUntil { !vm.isDeleting }
        XCTAssertTrue(vm.failed)

        mock.deleteAccountError = nil
        vm.deleteAccount()
        XCTAssertFalse(vm.failed)
        await waitUntil { !vm.isDeleting }
        XCTAssertEqual(mock.deleteAccountCalls, 2)
        XCTAssertFalse(vm.failed)
    }

    func testRepeatedTapWhileDeletingIsIgnored() async {
        let mock = MockRepository()
        let vm = DeleteAccountViewModel(repo: mock)
        vm.deleteAccount()
        vm.deleteAccount()
        await waitUntil { !vm.isDeleting }
        XCTAssertEqual(mock.deleteAccountCalls, 1)
    }
}
