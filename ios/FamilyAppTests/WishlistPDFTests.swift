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

    func testMakeProducesValidPDFFile() throws {
        let url = try XCTUnwrap(WishlistPDF.make(
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

    func testFileNameUsesWishlistNameAndSanitizes() throws {
        let url = try XCTUnwrap(WishlistPDF.make(name: "Mom's / Dad's list", subtitle: "", wishes: []))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        XCTAssertEqual(url.pathExtension, "pdf")
        // Path separators must not leak into the file name.
        XCTAssertFalse(url.lastPathComponent.contains("/"))
        XCTAssertTrue(url.lastPathComponent.hasPrefix("Mom's - Dad's list"))
    }

    func testEmptyWishesStillProducesPDF() throws {
        let url = try XCTUnwrap(WishlistPDF.make(name: "Empty", subtitle: "By Owner", wishes: []))
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }
}
