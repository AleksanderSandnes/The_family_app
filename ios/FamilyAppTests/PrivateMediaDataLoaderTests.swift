@testable import FamilyApp
import XCTest

final class PrivateMediaDataLoaderTests: XCTestCase {
    private let project = URL(string: "https://fictional.supabase.co")!
    private let publicURL = URL(string: "https://fictional.supabase.co/storage/v1/object/public/avatars/user/avatar.jpg")!
    private let signed = URL(string: "https://fictional.supabase.co/storage/v1/object/sign/avatars/user/avatar.jpg?token=fictional")!
    private var account: String? = "fictional-a"
    private var session: URLSession!

    override func setUp() {
        super.setUp()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.protocolClasses = [MediaURLProtocol.self]
        session = URLSession(configuration: configuration)
        MediaURLProtocol.handler = { _ in (200, Data("fictional-pixels".utf8)) }
    }

    override func tearDown() {
        session.invalidateAndCancel()
        MediaURLProtocol.handler = nil
        super.tearDown()
    }

    private func resolver() -> MediaURLResolver {
        MediaURLResolver(projectURL: project, accountID: { self.account }, sign: { _ in self.signed })
    }

    func testUsesSignedURLWithoutSessionHeaders() async throws {
        MediaURLProtocol.handler = { request in
            XCTAssertEqual(request.url, self.signed)
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertEqual(request.cachePolicy, .reloadIgnoringLocalCacheData)
            XCTAssertEqual(request.timeoutInterval, 10)
            return (200, Data("fictional-pixels".utf8))
        }
        let (data, _) = try await PrivateMediaDataLoader.download(
            url: publicURL, resolver: resolver(), timeout: 10, session: session
        )
        XCTAssertEqual(data, Data("fictional-pixels".utf8))
    }

    func testAccountSwitchDuringDownloadDiscardsTheResponse() async {
        MediaURLProtocol.handler = { _ in
            self.account = "fictional-b"
            return (200, Data("private-pixels".utf8))
        }
        do {
            _ = try await PrivateMediaDataLoader.download(url: publicURL, resolver: resolver(), session: session)
            XCTFail("Old account's pixels were returned")
        } catch {
            XCTAssertTrue(error is MediaAccessError)
        }
    }

    func testMissingSessionFailsBeforeFetchingAnyBytes() async {
        account = nil
        MediaURLProtocol.handler = { _ in
            XCTFail("Unauthenticated fetch")
            return (200, Data())
        }
        do {
            _ = try await PrivateMediaDataLoader.download(url: publicURL, resolver: resolver(), session: session)
            XCTFail("Unauthenticated media returned")
        } catch {
            XCTAssertTrue(error is MediaAccessError)
        }
    }

    func testHttpDenialsCannotBecomeImageOrAudioBytes() async {
        for status in [401, 403, 404, 500] {
            MediaURLProtocol.handler = { _ in (status, Data("provider-error".utf8)) }
            do {
                _ = try await PrivateMediaDataLoader.download(url: publicURL, resolver: resolver(), session: session)
                XCTFail("HTTP failure returned as media")
            } catch {
            XCTAssertTrue(error is MediaAccessError)
        }
        }
    }

    func testExternalImageDownloadNeedsNoSessionOrSignedLink() async throws {
        account = nil
        let url = URL(string: "https://shop.example/image.jpg")!
        MediaURLProtocol.handler = { request in
            XCTAssertEqual(request.url, url)
            return (200, Data("public-pixels".utf8))
        }
        let (data, _) = try await PrivateMediaDataLoader.download(url: url, resolver: resolver(), session: session)
        XCTAssertEqual(data, Data("public-pixels".utf8))
    }
}

private final class MediaURLProtocol: URLProtocol, @unchecked Sendable {
    static var handler: ((URLRequest) throws -> (Int, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        do {
            let (status, data) = try Self.handler!(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
