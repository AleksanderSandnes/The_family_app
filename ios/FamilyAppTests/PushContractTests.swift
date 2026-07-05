// Push contract — the iOS category identifiers must match what the edge functions
// put into the APNs payload (supabase/functions/_shared/fcm.ts + callers).
import XCTest
@testable import FamilyApp

final class PushContractTests: XCTestCase {
    func testCategoryIdentifiersMatchServerContract() {
        XCTAssertEqual(NotificationCategory.message, "MESSAGE")
        XCTAssertEqual(NotificationCategory.birthday, "BIRTHDAY")
        XCTAssertEqual(NotificationCategory.event, "EVENT")
    }
}
