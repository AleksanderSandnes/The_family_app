// Behaviour tests for ShoppingViewModel via MockRepository + NoopRealtimeObserver.
@testable import FamilyApp
import XCTest

@MainActor
final class ShoppingViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> ShoppingViewModel {
        ShoppingViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    // MARK: - Lists

    func testLoadListsFromRepo() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "l1"
        list.title = "Groceries"
        list.familyId = "f1"
        mock.shoppingListsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.lists.contains { $0.title == "Groceries" } }
        XCTAssertTrue(vm.lists.contains { $0.id == "l1" })
    }

    func testAddListInsertsWithCorrectFieldsAndOptimistic() async {
        let mock = makeMock()
        // Server echoes the new list back on the post-insert reload.
        var serverList = ShoppingListModel()
        serverList.id = "l1"
        serverList.title = "Party"
        serverList.ownerUserId = "u1"
        serverList.familyId = "f1"
        serverList.icon = "cake"
        serverList.color = 0x6366F1
        mock.shoppingListsForUserResult = [serverList]
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.addList(title: "Party", icon: "cake", color: 0x6366F1)
        await waitUntil { !mock.insertedShoppingLists.isEmpty }
        let inserted = mock.insertedShoppingLists.first
        XCTAssertEqual(inserted?.title, "Party")
        XCTAssertEqual(inserted?.icon, "cake")
        XCTAssertEqual(inserted?.color, 0x6366F1)
        XCTAssertEqual(inserted?.ownerUserId, "u1")
        XCTAssertEqual(inserted?.familyId, "f1")
        // The list ends up visible after the optimistic append + reload.
        await waitUntil { vm.lists.contains { $0.title == "Party" } }
        XCTAssertTrue(vm.lists.contains { $0.title == "Party" })
    }

    func testChangeListColorCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "l1"
        list.familyId = "f1"
        mock.shoppingListsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.lists.contains { $0.id == "l1" } }

        // Server reflects the new color on the post-update reload.
        var updated = list
        updated.color = 0xEC4899
        mock.shoppingListsForUserResult = [updated]
        vm.changeListColor(listId: "l1", color: 0xEC4899)
        await waitUntil { !mock.shoppingListColors.isEmpty }
        XCTAssertEqual(mock.shoppingListColors.first?.id, "l1")
        XCTAssertEqual(mock.shoppingListColors.first?.color, 0xEC4899)
        await waitUntil { vm.lists.contains { $0.id == "l1" && $0.color == 0xEC4899 } }
        XCTAssertTrue(vm.lists.contains { $0.id == "l1" && $0.color == 0xEC4899 })
    }

    func testChangeListIconCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "l1"
        list.familyId = "f1"
        mock.shoppingListsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.lists.contains { $0.id == "l1" } }

        // Server reflects the new icon on the post-update reload.
        var updated = list
        updated.icon = "pizza"
        mock.shoppingListsForUserResult = [updated]
        vm.changeListIcon(listId: "l1", icon: "pizza")
        await waitUntil { !mock.shoppingListIcons.isEmpty }
        XCTAssertEqual(mock.shoppingListIcons.first?.id, "l1")
        XCTAssertEqual(mock.shoppingListIcons.first?.icon, "pizza")
        await waitUntil { vm.lists.contains { $0.id == "l1" && $0.icon == "pizza" } }
        XCTAssertTrue(vm.lists.contains { $0.id == "l1" && $0.icon == "pizza" })
    }

    func testRenameListCallsRepoAndUpdatesSelected() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "l1"
        list.title = "Old"
        mock.shoppingListDetailResult = [list]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadListDetail("l1")
        await waitUntil { vm.selectedList?.id == "l1" }

        vm.renameList(listId: "l1", newTitle: "New")
        await waitUntil { !mock.renamedShoppingLists.isEmpty }
        XCTAssertEqual(mock.renamedShoppingLists.first?.id, "l1")
        XCTAssertEqual(mock.renamedShoppingLists.first?.title, "New")
        XCTAssertEqual(vm.selectedList?.title, "New")
    }

    func testDeleteListRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "gone"
        list.familyId = "f1"
        mock.shoppingListsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.lists.contains { $0.id == "gone" } }

        // Server no longer returns the list after the delete.
        mock.shoppingListsForUserResult = []
        vm.deleteList(list)
        await waitUntil { !mock.deletedShoppingListIds.isEmpty }
        XCTAssertEqual(mock.deletedShoppingListIds.first, "gone")
        await waitUntil { !vm.lists.contains { $0.id == "gone" } }
        XCTAssertFalse(vm.lists.contains { $0.id == "gone" })
    }

    func testLoadListDetailSetsSelectedListAndItems() async {
        let mock = makeMock()
        var list = ShoppingListModel()
        list.id = "l1"
        list.title = "Detail"
        mock.shoppingListDetailResult = [list]
        var item = ShoppingItemModel()
        item.id = "i1"
        item.listId = "l1"
        item.item = "Milk"
        mock.shoppingItemsForListResult = [item]
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.loadListDetail("l1")
        await waitUntil { vm.items.contains { $0.id == "i1" } }
        XCTAssertEqual(vm.selectedList?.title, "Detail")
        XCTAssertTrue(vm.items.contains { $0.item == "Milk" })
    }

    // MARK: - Items

    func testAddItemInsertsAndOptimistic() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.addItem(listId: "l1", item: "Bread")
        await waitUntil { !mock.insertedShoppingItems.isEmpty }
        XCTAssertEqual(mock.insertedShoppingItems.first?.listId, "l1")
        XCTAssertEqual(mock.insertedShoppingItems.first?.item, "Bread")
        XCTAssertTrue(vm.items.contains { $0.item == "Bread" })
    }

    func testToggleItemFlipsCheckedAndCallsRepo() async {
        let mock = makeMock()
        var item = ShoppingItemModel()
        item.id = "i1"
        item.listId = "l1"
        item.checked = false
        mock.shoppingItemsForListResult = [item]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadListDetail("l1")
        await waitUntil { vm.items.contains { $0.id == "i1" } }

        vm.toggle(item)
        await waitUntil { !mock.shoppingItemChecks.isEmpty }
        XCTAssertEqual(mock.shoppingItemChecks.first?.id, "i1")
        XCTAssertEqual(mock.shoppingItemChecks.first?.checked, true)
    }

    func testRenameItemCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var item = ShoppingItemModel()
        item.id = "i1"
        item.listId = "l1"
        item.item = "Milk"
        mock.shoppingItemsForListResult = [item]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadListDetail("l1")
        await waitUntil { vm.items.contains { $0.id == "i1" } }

        vm.renameItem(item, newName: "Oat milk")
        await waitUntil { !mock.renamedShoppingItems.isEmpty }
        XCTAssertEqual(mock.renamedShoppingItems.first?.id, "i1")
        XCTAssertEqual(mock.renamedShoppingItems.first?.item, "Oat milk")
    }

    func testDeleteItemRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var item = ShoppingItemModel()
        item.id = "i1"
        item.listId = "l1"
        mock.shoppingItemsForListResult = [item]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadListDetail("l1")
        await waitUntil { vm.items.contains { $0.id == "i1" } }

        mock.shoppingItemsForListResult = []
        vm.deleteItem(item)
        await waitUntil { !mock.deletedShoppingItemIds.isEmpty }
        XCTAssertEqual(mock.deletedShoppingItemIds.first, "i1")
        await waitUntil { !vm.items.contains { $0.id == "i1" } }
        XCTAssertFalse(vm.items.contains { $0.id == "i1" })
    }

    func testClearCompletedRemovesCheckedAndCallsRepo() async {
        let mock = makeMock()
        var checkedItem = ShoppingItemModel()
        checkedItem.id = "done"
        checkedItem.listId = "l1"
        checkedItem.checked = true
        var openItem = ShoppingItemModel()
        openItem.id = "open"
        openItem.listId = "l1"
        openItem.checked = false
        mock.shoppingItemsForListResult = [checkedItem, openItem]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadListDetail("l1")
        await waitUntil { vm.items.count == 2 }

        // Server returns only the open item after the checked one is cleared.
        mock.shoppingItemsForListResult = [openItem]
        vm.clearCompleted(listId: "l1")
        await waitUntil { !mock.clearedCompletedListIds.isEmpty }
        XCTAssertEqual(mock.clearedCompletedListIds.first, "l1")
        await waitUntil { !vm.items.contains { $0.checked } }
        XCTAssertFalse(vm.items.contains { $0.id == "done" })
    }
}
