@testable import FamilyApp

// Wishlist pure-logic tests for wishTitle and reservation-state branches.
import XCTest

final class WishlistLogicTests: XCTestCase {
    private func wish(_ text: String, price: String? = nil) -> WishModel {
        var wish = WishModel()
        wish.text = text
        wish.price = price
        return wish
    }

    func testWishTitleWithoutPrice() {
        XCTAssertEqual(wishTitle(wish("Lego set")), "Lego set")
        XCTAssertEqual(wishTitle(wish("Lego set", price: "  ")), "Lego set")
    }

    func testWishTitleAppendsPrice() {
        XCTAssertEqual(wishTitle(wish("Lego set", price: "499 kr")), "Lego set  ·  499 kr")
    }

    func testReservationStates() {
        var reservation = WishReservationModel()
        reservation.reservedBy = "u2"

        XCTAssertEqual(reservationState(reservation: nil, currentUserId: "u1"), .available)
        XCTAssertEqual(
            reservationState(reservation: reservation, currentUserId: "u2"),
            .reservedByMe
        )
        XCTAssertEqual(
            reservationState(reservation: reservation, currentUserId: "u1"),
            .reservedByOther
        )
        // Signed-out edge: someone else's reservation is never "mine".
        XCTAssertEqual(
            reservationState(reservation: reservation, currentUserId: nil),
            .reservedByOther
        )
    }

    func testUncheckedWishesSortFirst() {
        var checked = WishModel()
        checked.id = "a"
        checked.checked = true
        var unchecked = WishModel()
        unchecked.id = "b"
        let sorted = [checked, unchecked].sorted { !$0.checked && $1.checked }
        XCTAssertEqual(sorted.map(\.id), ["b", "a"])
    }

    func testWishlistIconOptionsAllMapped() {
        XCTAssertEqual(IconOptions.wishlist.count, 12)
        XCTAssertEqual(IconKeyMap.wishlistSymbol("card_giftcard"), "gift.fill")
        XCTAssertEqual(IconKeyMap.wishlistSymbol("unknown"), "gift.fill")
        for key in IconOptions.wishlist {
            XCTAssertNotEqual(IconKeyMap.symbol(key, fallback: "MISSING"), "MISSING", key)
        }
    }
}
