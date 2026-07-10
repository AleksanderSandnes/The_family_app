@testable import FamilyApp

// Shopping + meal pure-logic tests for label/date rules.
import XCTest

final class ShoppingLogicTests: XCTestCase {
    func testProgressLabelNoItems() {
        XCTAssertEqual(shoppingProgressLabel(nil), "No items yet")
        XCTAssertEqual(shoppingProgressLabel(ListProgress(bought: 0, total: 0)), "No items yet")
    }

    func testProgressLabelAllBought() {
        XCTAssertEqual(shoppingProgressLabel(ListProgress(bought: 3, total: 3)), "All bought")
    }

    func testProgressLabelPartial() {
        XCTAssertEqual(shoppingProgressLabel(ListProgress(bought: 1, total: 4)), "1 of 4 bought")
    }

    func testShoppingIconFallback() {
        XCTAssertEqual(IconKeyMap.shoppingSymbol("shopping_cart"), "cart.fill")
        XCTAssertEqual(IconKeyMap.shoppingSymbol("unknown_key"), "cart.fill")
        XCTAssertEqual(IconKeyMap.shoppingSymbol("pets"), "pawprint.fill")
    }

    func testShoppingIconOptionsMatchAndroidDialog() {
        XCTAssertEqual(IconOptions.shopping.count, 12)
        XCTAssertEqual(IconOptions.shopping.first, "shopping_cart")
        // Every option must resolve to a real symbol mapping (not the fallback by absence).
        for key in IconOptions.shopping {
            XCTAssertNotEqual(IconKeyMap.symbol(key, fallback: "MISSING"), "MISSING", key)
        }
    }
}

final class MealLogicTests: XCTestCase {
    func testIsoWeekNumber() {
        // 2026-01-01 is a Thursday → ISO week 1.
        XCTAssertEqual(isoWeekNumber(of: LocalDate(iso: "2026-01-01")!), 1)
        // 2026-07-05 is a Sunday in ISO week 27.
        XCTAssertEqual(isoWeekNumber(of: LocalDate(iso: "2026-07-05")!), 27)
        // 2024-12-30 (Monday) belongs to ISO week 1 of 2025.
        XCTAssertEqual(isoWeekNumber(of: LocalDate(iso: "2024-12-30")!), 1)
    }

    func testFormatMealDate() {
        XCTAssertEqual(formatMealDate("2026-07-05"), "05 Jul")
        XCTAssertEqual(formatMealDate("2026-12-24"), "24 Dec")
        XCTAssertEqual(formatMealDate("garbage"), "garbage")
    }

    func testMealPlanLabelWithProgress() {
        XCTAssertEqual(
            mealPlanLabel(
                progress: MealProgress(planned: 2, total: 7),
                fromIso: "2026-07-06", toIso: "2026-07-12"
            ),
            "2 of 7 dinners planned"
        )
    }

    func testMealPlanLabelFallsBackToDayCount() {
        XCTAssertEqual(
            mealPlanLabel(progress: nil, fromIso: "2026-07-06", toIso: "2026-07-12"),
            "7 days"
        )
        XCTAssertEqual(
            mealPlanLabel(
                progress: MealProgress(planned: 0, total: 0),
                fromIso: "2026-07-06", toIso: "2026-07-06"
            ),
            "1 days"
        )
        XCTAssertEqual(mealPlanLabel(progress: nil, fromIso: "bad", toIso: "worse"), "0 days")
    }

    func testFullDayNameEnglish() {
        let english = Locale(identifier: "en_US_POSIX")
        XCTAssertEqual(LocalDate(iso: "2026-07-06")!.fullDayName(locale: english), "Monday")
        XCTAssertEqual(LocalDate(iso: "2026-07-05")!.fullDayName(locale: english), "Sunday")
    }

    func testMealIconFallback() {
        XCTAssertEqual(IconKeyMap.mealSymbol("restaurant"), "fork.knife")
        XCTAssertEqual(IconKeyMap.mealSymbol("nope"), "fork.knife")
        for key in IconOptions.meal {
            XCTAssertNotEqual(IconKeyMap.symbol(key, fallback: "MISSING"), "MISSING", key)
        }
    }
}
