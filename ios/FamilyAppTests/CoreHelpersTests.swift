// Small core helpers: ThemeMode mapping, isoNow format, ARGB color decoding.
import SwiftUI
import XCTest
@testable import FamilyApp

final class CoreHelpersTests: XCTestCase {
    func testThemeModeRawValuesMatchAndroid() {
        XCTAssertEqual(ThemeMode.system.rawValue, "SYSTEM")
        XCTAssertEqual(ThemeMode.light.rawValue, "LIGHT")
        XCTAssertEqual(ThemeMode.dark.rawValue, "DARK")
    }

    func testThemeModeColorScheme() {
        XCTAssertNil(ThemeMode.system.colorScheme)
        XCTAssertEqual(ThemeMode.light.colorScheme, .light)
        XCTAssertEqual(ThemeMode.dark.colorScheme, .dark)
    }

    func testIsoNowIsIso8601WithFractionalSeconds() {
        let value = isoNow()
        // e.g. 2026-07-05T12:34:56.789Z
        let pattern = #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z$"#
        XCTAssertNotNil(value.range(of: pattern, options: .regularExpression), value)
    }

    func testArgbColorRoundTrip() {
        // 0xFF6366F1 (indigo500 in the avatar palette) as a signed 32-bit Android int.
        let argb = Int(Int32(bitPattern: 0xFF6366F1))
        let color = UIColor(Color(argb: argb))
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        XCTAssertEqual(red, 0x63 / 255, accuracy: 0.005)
        XCTAssertEqual(green, 0x66 / 255, accuracy: 0.005)
        XCTAssertEqual(blue, 0xF1 / 255, accuracy: 0.005)
        XCTAssertEqual(alpha, 1, accuracy: 0.005)
    }
}
