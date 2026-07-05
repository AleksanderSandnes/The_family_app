// Family/profile/settings pure-logic tests.
import XCTest
@testable import FamilyApp

final class FamilyLogicTests: XCTestCase {
    func testGenerateJoinCodeShape() {
        for _ in 0..<20 {
            let code = generateJoinCode()
            XCTAssertEqual(code.count, 8)
            XCTAssertEqual(code, code.uppercased())
            // UUID prefix chars: hex digits and dashes.
            XCTAssertTrue(code.allSatisfy { $0.isHexDigit || $0 == "-" }, code)
        }
    }

    func testGenerateJoinCodeIsRandom() {
        XCTAssertNotEqual(generateJoinCode(), generateJoinCode())
    }

    func testInviteMessageContainsLinkAndCode() {
        let message = inviteMessage(familyName: "Sandnes", joinCode: "ABC12345")
        XCTAssertTrue(message.contains("\"Sandnes\""))
        XCTAssertTrue(message.contains("familyapp://join?code=ABC12345"))
        XCTAssertTrue(message.contains("enter: ABC12345"))
    }

    func testQrGeneratorProducesImage() {
        let image = generateQrImage(content: "familyapp://join?code=TESTFAM", size: 128)
        XCTAssertNotNil(image)
        XCTAssertGreaterThanOrEqual(image?.size.width ?? 0, 128)
    }
}

final class ProfileLogicTests: XCTestCase {
    func testFormatBirthday() {
        XCTAssertEqual(formatBirthday("1990-12-24"), "December 24, 1990")
        XCTAssertEqual(formatBirthday(nil), "—")
        XCTAssertEqual(formatBirthday(""), "—")
        XCTAssertEqual(formatBirthday("24 Dec"), "24 Dec") // unparsable falls through raw
    }
}

final class SettingsLogicTests: XCTestCase {
    func testLeadTimeOptionsMatchAndroid() {
        XCTAssertEqual(leadTimeOptions.map(\.days), [0, 1, 2, 7])
        XCTAssertEqual(leadTimeOptions.map(\.label), ["Same day", "1 day", "2 days", "7 days"])
    }

    func testThemeModeLabels() {
        XCTAssertEqual(ThemeMode.allCases.map(\.label), ["System", "Light", "Dark"])
    }
}

final class ImageUtilsTests: XCTestCase {
    private func solidImage(width: Int, height: Int) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        return renderer.image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    func testDownscalesToMaxDimension() {
        let big = solidImage(width: 4000, height: 2000)
        let data = ImageUtils.compressWithOrientation(big, maxDimension: 1024)
        XCTAssertNotNil(data)
        let result = UIImage(data: data!)!
        XCTAssertLessThanOrEqual(max(result.size.width, result.size.height), 1024)
        // Aspect ratio preserved (2:1).
        XCTAssertEqual(result.size.width / result.size.height, 2, accuracy: 0.05)
    }

    func testSmallImagesAreNotUpscaled() {
        let small = solidImage(width: 100, height: 80)
        let data = ImageUtils.compressWithOrientation(small, maxDimension: 1024)
        let result = UIImage(data: data!)!
        XCTAssertEqual(result.size.width, 100, accuracy: 1)
    }

    func testGarbageDataReturnsNil() {
        XCTAssertNil(ImageUtils.compressWithOrientation(Data([0x00, 0x01, 0x02])))
    }
}
