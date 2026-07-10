@testable import FamilyApp

// FamilyRepository.palette picks an avatar color via Java String.hashCode over an 8-color table.
import XCTest

@MainActor
final class AvatarPaletteTests: XCTestCase {
    func testJavaHashCodeMatchesKnownJavaValues() {
        // Canonical values from java.lang.String.hashCode().
        XCTAssertEqual(FamilyRepository.javaHashCode(""), 0)
        XCTAssertEqual(FamilyRepository.javaHashCode("a"), 97)
        XCTAssertEqual(FamilyRepository.javaHashCode("abc"), 96354)
        XCTAssertEqual(FamilyRepository.javaHashCode("hello"), 99162322)
        // Overflow case — Java wraps to negative.
        XCTAssertEqual(FamilyRepository.javaHashCode("polygenelubricants"), -2147483648)
    }

    func testPaletteHasEightColors() {
        XCTAssertEqual(FamilyRepository.avatarColors.count, 8)
    }

    func testPaletteIsDeterministicAndInBounds() {
        for seed in ["", "Alice", "Bob", "Åse Marie", "👨‍👩‍👧‍👦", "polygenelubricants"] {
            let first = FamilyRepository.palette(seed)
            let second = FamilyRepository.palette(seed)
            XCTAssertEqual(first, second, "palette must be deterministic for \(seed)")
            XCTAssertTrue(FamilyRepository.avatarColors.contains(first))
        }
    }

    func testPaletteMatchesKotlinForKnownSeeds() {
        // Kotlin: avatarColors[(seed.hashCode() and Int.MAX_VALUE) % 8]
        // "abc" → 96354 & MAX = 96354 → % 8 = 2 → 0xFF14B8A6
        XCTAssertEqual(
            FamilyRepository.palette("abc"),
            FamilyRepository.avatarColors[96354 % 8]
        )
        // "hello" → 99162322 % 8 = 2
        XCTAssertEqual(
            FamilyRepository.palette("hello"),
            FamilyRepository.avatarColors[99162322 % 8]
        )
        // Int.MIN_VALUE & Int.MAX_VALUE == 0 → index 0
        XCTAssertEqual(
            FamilyRepository.palette("polygenelubricants"),
            FamilyRepository.avatarColors[0]
        )
    }
}
