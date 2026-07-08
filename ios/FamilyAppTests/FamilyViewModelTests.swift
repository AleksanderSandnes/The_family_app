// Behaviour tests for FamilyViewModel via the injected MockRepository (no live backend).
@testable import FamilyApp
import XCTest

@MainActor
final class FamilyViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.name = "Alice"
        user.familyId = familyId
        mock.users[userId] = user
        var family = FamilyModel()
        family.id = familyId
        family.name = "Smiths"
        mock.families[familyId] = family
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> FamilyViewModel {
        FamilyViewModel(repo: mock)
    }

    // MARK: - Load / relations

    func testLoadPopulatesFamilyMembersAndRelations() async {
        let mock = makeMock()
        var member = UserModel()
        member.id = "u2"
        member.name = "Bob"
        mock.familyMembers = [member]
        mock.relations = ["u2": "Brother"]
        let vm = makeVM(mock)
        await waitUntil { vm.members.contains { $0.id == "u2" } }
        XCTAssertEqual(vm.family?.name, "Smiths")
        XCTAssertTrue(vm.members.contains { $0.name == "Bob" })
        XCTAssertEqual(vm.relations["u2"], "Brother")
    }

    func testRefreshReloadsMembers() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { vm.family != nil }
        var member = UserModel()
        member.id = "u3"
        member.name = "Cara"
        mock.familyMembers = [member]
        vm.refresh()
        await waitUntil { vm.members.contains { $0.id == "u3" } }
        XCTAssertTrue(vm.members.contains { $0.name == "Cara" })
    }

    func testSetRelationCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { vm.family != nil }

        vm.setRelation(toUserId: "u2", relation: "Dad")
        // Optimistic local update is synchronous.
        XCTAssertEqual(vm.relations["u2"], "Dad")
        await waitUntil { !mock.setRelations.isEmpty }
        XCTAssertEqual(
            mock.setRelations.first,
            MockRepository.RelationRecord(from: "u1", to: "u2", relation: "Dad")
        )
    }

    func testSetRelationEmptyClearsLocally() async {
        let mock = makeMock()
        mock.relations = ["u2": "Dad"]
        let vm = makeVM(mock)
        await waitUntil { vm.relations["u2"] == "Dad" }

        vm.setRelation(toUserId: "u2", relation: "   ")
        XCTAssertNil(vm.relations["u2"])
    }

    // MARK: - Family lifecycle calls repo

    func testRenameFamilyCallsRepo() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { vm.family != nil }

        vm.renameFamily("New Name")
        await waitUntil { mock.renamedFamilies.contains { $0.name == "New Name" } }
        XCTAssertTrue(mock.renamedFamilies.contains { $0.familyId == "f1" && $0.name == "New Name" })
    }

    func testLeaveFamilyCallsRepo() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { vm.family != nil }

        vm.leaveFamily()
        await waitUntil { !mock.leaveFamilyCalls.isEmpty }
        XCTAssertEqual(mock.leaveFamilyCalls.first, "u1")
    }

    func testRemoveMemberCallsRepo() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { vm.family != nil }

        vm.removeMember("u2")
        await waitUntil { !mock.removedMembers.isEmpty }
        XCTAssertEqual(mock.removedMembers.first, "u2")
    }

    // MARK: - Join-code generation shape

    func testGenerateJoinCodeShape() {
        let code = generateJoinCode()
        XCTAssertEqual(code.count, 8)
        XCTAssertEqual(code, code.uppercased())
        XCTAssertTrue(code.allSatisfy(\.isHexDigit))
    }
}
