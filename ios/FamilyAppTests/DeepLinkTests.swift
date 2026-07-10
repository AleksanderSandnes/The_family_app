@testable import FamilyApp

// DeepLink.parse — familyapp://auth, familyapp://chat/{id}, familyapp://join?code=.
import XCTest

final class DeepLinkTests: XCTestCase {
    func testAuthLinkParses() {
        let url = URL(string: "familyapp://auth#access_token=abc")!
        XCTAssertEqual(DeepLink.parse(url), .auth(url))
    }

    func testChatLinkParses() {
        let url = URL(string: "familyapp://chat/conv-42")!
        XCTAssertEqual(DeepLink.parse(url), .chat(conversationId: "conv-42"))
    }

    func testChatLinkWithoutIdIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://chat")!))
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://chat/")!))
    }

    func testJoinLinkParsesCode() {
        let url = URL(string: "familyapp://join?code=TESTFAM")!
        XCTAssertEqual(DeepLink.parse(url), .join(code: "TESTFAM"))
    }

    func testJoinLinkWithoutCodeIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://join")!))
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://join?code=")!))
    }

    func testWishlistShareLinkParsesToken() {
        let url = URL(string: "familyapp://wishlist?token=abc-123")!
        XCTAssertEqual(DeepLink.parse(url), .wishlistShare(token: "abc-123"))
    }

    func testWishlistShareLinkWithoutTokenIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://wishlist")!))
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://wishlist?token=")!))
    }

    func testForeignSchemeIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "https://example.com/join?code=X")!))
        XCTAssertNil(DeepLink.parse(URL(string: "otherapp://auth")!))
    }

    func testUnknownHostIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://unknown")!))
    }
}
