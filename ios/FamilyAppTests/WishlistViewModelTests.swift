// Behaviour tests for WishlistViewModel via MockRepository + NoopRealtimeObserver.
@testable import FamilyApp
import XCTest

@MainActor
final class WishlistViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> WishlistViewModel {
        WishlistViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    // MARK: - Wishlists

    func testLoadWishlistsFromRepo() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "w1"
        list.name = "Birthday"
        list.familyId = "f1"
        mock.wishlistsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.wishlists.contains { $0.name == "Birthday" } }
        XCTAssertTrue(vm.wishlists.contains { $0.id == "w1" })
    }

    func testAddWishlistInsertsWithCorrectFieldsAndOptimistic() async {
        let mock = makeMock()
        var serverList = WishlistModel()
        serverList.id = "w1"
        serverList.name = "Christmas"
        serverList.ownerUserId = "u1"
        serverList.familyId = "f1"
        serverList.icon = "cake"
        serverList.color = 0x6366F1
        mock.wishlistsForUserResult = [serverList]
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.addWishlist(name: "Christmas", icon: "cake", color: 0x6366F1)
        await waitUntil { !mock.insertedWishlists.isEmpty }
        let inserted = mock.insertedWishlists.first
        XCTAssertEqual(inserted?.name, "Christmas")
        XCTAssertEqual(inserted?.icon, "cake")
        XCTAssertEqual(inserted?.color, 0x6366F1)
        XCTAssertEqual(inserted?.ownerUserId, "u1")
        XCTAssertEqual(inserted?.familyId, "f1")
        await waitUntil { vm.wishlists.contains { $0.name == "Christmas" } }
        XCTAssertTrue(vm.wishlists.contains { $0.name == "Christmas" })
    }

    func testRenameWishlistCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "w1"
        list.name = "Old"
        list.familyId = "f1"
        mock.wishlistsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.wishlists.contains { $0.id == "w1" } }

        vm.renameWishlist(wishlistId: "w1", newName: "New")
        await waitUntil { !mock.renamedWishlists.isEmpty }
        XCTAssertEqual(mock.renamedWishlists.first?.id, "w1")
        XCTAssertEqual(mock.renamedWishlists.first?.name, "New")
        await waitUntil { vm.wishlists.contains { $0.id == "w1" && $0.name == "New" } }
        XCTAssertTrue(vm.wishlists.contains { $0.id == "w1" && $0.name == "New" })
    }

    func testChangeWishlistIconCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "w1"
        list.familyId = "f1"
        mock.wishlistsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.wishlists.contains { $0.id == "w1" } }

        vm.changeWishlistIcon(wishlistId: "w1", newIcon: "star")
        await waitUntil { !mock.wishlistIcons.isEmpty }
        XCTAssertEqual(mock.wishlistIcons.first?.id, "w1")
        XCTAssertEqual(mock.wishlistIcons.first?.icon, "star")
        await waitUntil { vm.wishlists.contains { $0.id == "w1" && $0.icon == "star" } }
        XCTAssertTrue(vm.wishlists.contains { $0.id == "w1" && $0.icon == "star" })
    }

    func testChangeWishlistColorCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "w1"
        list.familyId = "f1"
        mock.wishlistsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.wishlists.contains { $0.id == "w1" } }

        vm.changeWishlistColor(wishlistId: "w1", color: 0xEC4899)
        await waitUntil { !mock.wishlistColors.isEmpty }
        XCTAssertEqual(mock.wishlistColors.first?.id, "w1")
        XCTAssertEqual(mock.wishlistColors.first?.color, 0xEC4899)
        await waitUntil { vm.wishlists.contains { $0.id == "w1" && $0.color == 0xEC4899 } }
        XCTAssertTrue(vm.wishlists.contains { $0.id == "w1" && $0.color == 0xEC4899 })
    }

    func testDeleteWishlistRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "gone"
        list.familyId = "f1"
        mock.wishlistsForUserResult = [list]
        let vm = makeVM(mock)
        await waitUntil { vm.wishlists.contains { $0.id == "gone" } }

        mock.wishlistsForUserResult = []
        vm.deleteWishlist(list)
        await waitUntil { !mock.deletedWishlistIds.isEmpty }
        XCTAssertEqual(mock.deletedWishlistIds.first, "gone")
        await waitUntil { !vm.wishlists.contains { $0.id == "gone" } }
        XCTAssertFalse(vm.wishlists.contains { $0.id == "gone" })
    }

    // MARK: - Detail + wishes

    func testLoadWishlistDetailSetsSelectedAndWishes() async {
        let mock = makeMock()
        var list = WishlistModel()
        list.id = "w1"
        list.name = "Detail"
        list.ownerUserId = "u1"
        mock.wishlistDetailResult = [list]
        var wish = WishModel()
        wish.id = "wish1"
        wish.wishlistId = "w1"
        wish.text = "Lego"
        mock.wishesForListResult = [wish]
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.loadWishlistDetail("w1")
        await waitUntil { vm.wishes.contains { $0.id == "wish1" } }
        XCTAssertEqual(vm.selectedWishlist?.name, "Detail")
        XCTAssertTrue(vm.wishes.contains { $0.text == "Lego" })
    }

    func testAddWishInsertsAndOptimistic() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.addWish(wishlistId: "w1", draft: WishDraft(text: "Bike", link: nil, price: "$100", imageData: nil))
        await waitUntil { !mock.insertedWishes.isEmpty }
        let inserted = mock.insertedWishes.first
        XCTAssertEqual(inserted?.wishlistId, "w1")
        XCTAssertEqual(inserted?.userId, "u1")
        XCTAssertEqual(inserted?.text, "Bike")
        XCTAssertEqual(inserted?.price, "$100")
        XCTAssertTrue(vm.wishes.contains { $0.text == "Bike" })
    }

    func testToggleWishFlipsCheckedAndCallsRepo() async {
        let mock = makeMock()
        var wish = WishModel()
        wish.id = "wish1"
        wish.wishlistId = "w1"
        wish.checked = false
        mock.wishesForListResult = [wish]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadWishlistDetail("w1")
        await waitUntil { vm.wishes.contains { $0.id == "wish1" } }

        vm.toggle(wish)
        await waitUntil { !mock.wishChecks.isEmpty }
        XCTAssertEqual(mock.wishChecks.first?.id, "wish1")
        XCTAssertEqual(mock.wishChecks.first?.checked, true)
    }

    func testDeleteWishRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var wish = WishModel()
        wish.id = "wish1"
        wish.wishlistId = "w1"
        mock.wishesForListResult = [wish]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadWishlistDetail("w1")
        await waitUntil { vm.wishes.contains { $0.id == "wish1" } }

        mock.wishesForListResult = []
        vm.deleteWish(wish)
        await waitUntil { !mock.deletedWishIds.isEmpty }
        XCTAssertEqual(mock.deletedWishIds.first, "wish1")
        await waitUntil { !vm.wishes.contains { $0.id == "wish1" } }
        XCTAssertFalse(vm.wishes.contains { $0.id == "wish1" })
    }

    // MARK: - Reservations (secret gift claims)

    func testReserveCallsRepoInsertAndReflectsReservation() async {
        let mock = makeMock()
        var wish = WishModel()
        wish.id = "wish1"
        wish.wishlistId = "w1"
        mock.wishesForListResult = [wish]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadWishlistDetail("w1")
        await waitUntil { vm.wishes.contains { $0.id == "wish1" } }

        // Server reflects the new reservation on the post-insert reload.
        var reservation = WishReservationModel()
        reservation.id = "r1"
        reservation.wishId = "wish1"
        reservation.reservedBy = "u1"
        mock.wishReservationsResult = [reservation]
        vm.reserve(wish)
        await waitUntil { !mock.insertedReservations.isEmpty }
        XCTAssertEqual(mock.insertedReservations.first?.wishId, "wish1")
        XCTAssertEqual(mock.insertedReservations.first?.reservedBy, "u1")
        await waitUntil { vm.reservations["wish1"]?.reservedBy == "u1" }
        XCTAssertEqual(vm.reservations["wish1"]?.reservedBy, "u1")
    }

    func testUnreserveCallsRepoDeleteAndClearsReservation() async {
        let mock = makeMock()
        var wish = WishModel()
        wish.id = "wish1"
        wish.wishlistId = "w1"
        mock.wishesForListResult = [wish]
        var reservation = WishReservationModel()
        reservation.id = "r1"
        reservation.wishId = "wish1"
        reservation.reservedBy = "u1"
        mock.wishReservationsResult = [reservation]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadWishlistDetail("w1")
        await waitUntil { vm.reservations["wish1"] != nil }

        // After the delete, the server returns no reservations.
        mock.wishReservationsResult = []
        vm.unreserve(wish)
        await waitUntil { !mock.deletedReservations.isEmpty }
        XCTAssertEqual(mock.deletedReservations.first?.wishId, "wish1")
        XCTAssertEqual(mock.deletedReservations.first?.reservedBy, "u1")
        await waitUntil { vm.reservations["wish1"] == nil }
        XCTAssertNil(vm.reservations["wish1"])
    }

    func testUpdateWishCallsRepoWithNewValues() async {
        let mock = makeMock()
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.updateWish(
            wishId: "wi1",
            draft: WishDraft(text: "New title", link: "https://x.com", price: "99", imageData: nil)
        )
        await waitUntil { !mock.updatedWishes.isEmpty }

        let record = mock.updatedWishes.first
        XCTAssertEqual(record?.id, "wi1")
        XCTAssertEqual(record?.text, "New title")
        XCTAssertEqual(record?.link, "https://x.com")
        XCTAssertEqual(record?.price, "99")
    }

    // MARK: - Share links

    func testLoadWishlistsAlsoLoadsSharedWishlists() async {
        let mock = makeMock()
        var shared = WishlistModel()
        shared.id = "shared1"
        shared.name = "Grandma's list"
        shared.familyId = "other-family"
        mock.sharedWishlistsResult = [shared]
        let vm = makeVM(mock)
        await waitUntil { vm.sharedWishlists.contains { $0.id == "shared1" } }
        // The shared list is separate from my own/family wishlists.
        XCTAssertFalse(vm.wishlists.contains { $0.id == "shared1" })
    }

    func testShareLinkReturnsDeepLinkWithMintedToken() async {
        let mock = makeMock()
        mock.ensureShareTokenResult = "tok-123"
        let vm = makeVM(mock)
        await waitUntil { true }

        let url = await vm.shareLink(for: "w1")
        XCTAssertEqual(url, DeepLinkURL.sharedWishlist(token: "tok-123"))
        XCTAssertEqual(mock.ensuredShareTokenWishlistIds, ["w1"])
    }

    func testShareLinkReturnsNilWhenTokenUnavailable() async {
        let mock = makeMock()
        mock.ensureShareTokenResult = nil // e.g. caller isn't the owner
        let vm = makeVM(mock)
        await waitUntil { true }
        let url = await vm.shareLink(for: "w1")
        XCTAssertNil(url)
    }

    func testAcceptShareRedeemsTokenAndReturnsWishlistId() async {
        let mock = makeMock()
        mock.acceptShareResult = "shared-wl"
        let vm = makeVM(mock)
        await waitUntil { true }

        let wishlistId = await vm.acceptShare(token: "tok-abc")
        XCTAssertEqual(wishlistId, "shared-wl")
        XCTAssertEqual(mock.acceptedShareTokens, ["tok-abc"])
    }

    func testAcceptShareReturnsNilForInvalidToken() async {
        let mock = makeMock()
        mock.acceptShareResult = nil
        let vm = makeVM(mock)
        await waitUntil { true }
        let wishlistId = await vm.acceptShare(token: "bad")
        XCTAssertNil(wishlistId)
    }
}
