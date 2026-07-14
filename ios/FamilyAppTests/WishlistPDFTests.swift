@testable import FamilyApp
import XCTest

final class WishlistPDFTests: XCTestCase {
    private func wish(_ text: String, price: String? = nil, link: String? = nil) -> WishModel {
        var wish = WishModel()
        wish.id = UUID().uuidString
        wish.text = text
        wish.price = price
        wish.link = link
        return wish
    }

    func testMakeProducesValidPDFFile() async throws {
        let url = try XCTUnwrap(await WishlistPDF.make(
            name: "Birthday",
            subtitle: "By Test Nine",
            wishes: [wish("AirPods", price: "1990 kr", link: "apple.com/airpods"), wish("Cookbook")]
        ))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }

        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        let data = try Data(contentsOf: url)
        XCTAssertFalse(data.isEmpty)
        // Every PDF starts with the "%PDF" magic bytes.
        XCTAssertEqual(data.prefix(4), Data("%PDF".utf8))
    }

    func testFileNameUsesWishlistNameAndSanitizes() async throws {
        let url = try XCTUnwrap(await WishlistPDF.make(name: "Mom's / Dad's list", subtitle: "", wishes: []))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        XCTAssertEqual(url.pathExtension, "pdf")
        // Path separators must not leak into the file name.
        XCTAssertFalse(url.lastPathComponent.contains("/"))
        XCTAssertTrue(url.lastPathComponent.hasPrefix("Mom's - Dad's list"))
    }

    func testEmptyWishesStillProducesPDF() async throws {
        let url = try XCTUnwrap(await WishlistPDF.make(name: "Empty", subtitle: "By Owner", wishes: []))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    // MARK: - formatWishPrice (NOK)

    func testBareNumbersGainKrSuffix() {
        XCTAssertEqual(formatWishPrice("299"), "299 kr")
        XCTAssertEqual(formatWishPrice("299,50"), "299,50 kr")
        XCTAssertEqual(formatWishPrice("1990.00"), "1990.00 kr")
        XCTAssertEqual(formatWishPrice("1 990"), "1 990 kr")
    }

    func testNonNumericPricesRenderAsTyped() {
        XCTAssertEqual(formatWishPrice("1990 kr"), "1990 kr")
        XCTAssertEqual(formatWishPrice("ca. 500,-"), "ca. 500,-")
        XCTAssertEqual(formatWishPrice("$20"), "$20")
    }

    // MARK: - shortenedLink

    func testLinksDropSchemeWwwAndTrailingSlash() {
        XCTAssertEqual(shortenedLink("https://www.apple.com/airpods/"), "apple.com/airpods")
        XCTAssertEqual(shortenedLink("http://finn.no/item/123"), "finn.no/item/123")
    }

    func testLongLinksAreEllipsized() {
        let long = "https://example.com/" + String(repeating: "a", count: 100)
        let short = shortenedLink(long)
        XCTAssertLessThanOrEqual(short.count, 60)
        XCTAssertTrue(short.hasSuffix("…"))
    }

    func testMakeWithUnreachableImageStillProducesPDF() async throws {
        var lego = wish("Lego", price: "499")
        lego.imageUrl = "https://127.0.0.1:1/nope.jpg"
        let url = try XCTUnwrap(await WishlistPDF.make(name: "Gifts", subtitle: "", wishes: [lego]))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }
}
