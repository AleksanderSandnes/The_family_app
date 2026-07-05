// DeepLink.parse — familyapp://auth, familyapp://chat/{id}, familyapp://join?code=.
import XCTest
@testable import FamilyApp

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

    func testForeignSchemeIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "https://example.com/join?code=X")!))
        XCTAssertNil(DeepLink.parse(URL(string: "otherapp://auth")!))
    }

    func testUnknownHostIsRejected() {
        XCTAssertNil(DeepLink.parse(URL(string: "familyapp://unknown")!))
    }
}
