// Map pure-logic tests — mirror formatLastSeen in FamilyMapScreen.kt.
import XCTest
@testable import FamilyApp

final class MapLogicTests: XCTestCase {
    // Fixed "now": 2026-07-05T12:00:00Z
    private let now: Int64 = 1_783_252_800_000

    func testJustNowIncludesClockSkewFutures() {
        XCTAssertEqual(formatLastSeen("2026-07-05T11:59:31Z", nowMs: now), "Just now")
        XCTAssertEqual(formatLastSeen("2026-07-05T12:00:30Z", nowMs: now), "Just now")
    }

    func testMinutesAndHours() {
        XCTAssertEqual(formatLastSeen("2026-07-05T11:45:00Z", nowMs: now), "15 min ago")
        XCTAssertEqual(formatLastSeen("2026-07-05T09:00:00Z", nowMs: now), "3 hours ago")
    }

    func testOlderThanADayShowsDate() {
        XCTAssertEqual(formatLastSeen("2026-07-01T09:00:00Z", nowMs: now), "Jul 1, 2026")
    }

    func testFallbacks() {
        XCTAssertEqual(formatLastSeen(nil, nowMs: now), "Unknown")
        XCTAssertEqual(formatLastSeen("garbage", nowMs: now), "Location shared")
    }
}
