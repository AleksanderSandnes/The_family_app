@testable import FamilyApp
import Nuke
import XCTest

final class MediaURLResolverTests: XCTestCase {
    private let project = URL(string: "https://fictional.supabase.co")!
    private let publicURL = URL(string: "https://fictional.supabase.co/storage/v1/object/public/avatars/user/avatar.jpg?t=123")!
    private let signed = URL(string: "https://fictional.supabase.co/storage/v1/object/sign/avatars/user/avatar.jpg?token=fictional")!
    private var account: String? = "fictional-a"

    private func resolver(sign: ((StorageMediaReference) async throws -> URL)? = nil) -> MediaURLResolver {
        MediaURLResolver(projectURL: project, accountID: { self.account }, sign: sign ?? { _ in self.signed })
    }

    func testRecognizesAllProtectedBucketsAndPreservesUnicodeAndPlus() throws {
        for bucket in ["avatars", "group-images", "wish-images", "chat-media"] {
            for mode in ["public", "authenticated", "sign"] {
                let url = URL(string: "\(project)/storage/v1/object/\(mode)/\(bucket)/user/p%C3%A5+ss.jpg?token=old")!
                XCTAssertEqual(try resolver().reference(url), StorageMediaReference(bucket: bucket, path: "user/på+ss.jpg"))
            }
        }
    }

    func testUnsafePathsAndStorageRoutesAreRejected() {
        for path in ["", "user/", "user//file", "user/../file", "user/%2e%2e/file", "user/%252e%252e/file", "user/%2ffile", "user/%5cfile", "user/%00file"] {
            let url = URL(string: "\(project)/storage/v1/object/public/avatars/\(path)")!
            XCTAssertThrowsError(try resolver().reference(url), "Accepted unsafe path")
        }
        for url in [
            "\(project)/storage/v1/object/public/unknown/user/file",
            "\(project)/storage/v1/render/image/public/avatars/user/file",
            "\(publicURL)#fragment",
            publicURL.absoluteString.replacingOccurrences(of: "https:", with: "http:"),
            publicURL.absoluteString.replacingOccurrences(of: "fictional.supabase.co", with: "user@fictional.supabase.co"),
            publicURL.absoluteString.replacingOccurrences(of: "fictional.supabase.co", with: "fictional.supabase.co:444"),
        ] {
            XCTAssertThrowsError(try resolver().reference(URL(string: url)!))
        }
    }

    func testExternalAssetsAreNeverSignedAndNeedNoSession() async throws {
        account = nil
        let resolver = resolver { _ in XCTFail("External asset was signed"); return self.signed }
        for value in ["https://shop.example/image.jpg", "https://fictional.supabase.co.attacker.example/storage/v1/object/public/avatars/user/avatar.jpg", "\(project)/other/image.jpg"] {
            let url = URL(string: value)!
            XCTAssertNil(try resolver.reference(url))
            let resolved = try await resolver.resolve(url)
            XCTAssertEqual(resolved, url)
        }
    }

    func testStoredPublicAndStaleSignedReferencesAreAuthorizedAfresh() async throws {
        var count = 0
        let resolver = resolver { object in
            XCTAssertEqual(object, StorageMediaReference(bucket: "avatars", path: "user/avatar.jpg"))
            count += 1
            return self.signed
        }
        let resolved = try await resolver.resolve(publicURL)
        XCTAssertEqual(resolved.absoluteString, signed.absoluteString + "&t=123")
        let stale = URL(string: signed.absoluteString.replacingOccurrences(of: "token=fictional", with: "token=old"))!
        let refreshed = try await resolver.resolve(stale)
        XCTAssertEqual(refreshed, signed)
        XCTAssertEqual(count, 2)
    }

    func testMissingSessionSignOutAndAccountSwitchFailClosed() async {
        account = nil
        do {
            _ = try await resolver().resolve(publicURL)
            XCTFail("Missing session accepted")
        } catch { XCTAssertTrue(error is MediaAccessError) }
        let nextAccounts: [String?] = [nil, "fictional-b"]
        for next in nextAccounts {
            account = "fictional-a"
            let resolver = resolver { _ in self.account = next; return self.signed }
            do {
                _ = try await resolver.resolve(publicURL)
                XCTFail("Account change ignored")
            } catch { XCTAssertTrue(error is MediaAccessError) }
        }
    }

    func testFailedSigningDoesNotFallBackToPublicURLs() async {
        let resolver = resolver { _ in throw URLError(.notConnectedToInternet) }
        do {
            _ = try await resolver.resolve(publicURL)
            XCTFail("Failed signing fell back to public access")
        } catch { XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet) }
    }

    func testSignerCannotSubstituteAnotherOriginOrObject() async {
        for result in [
            signed.absoluteString.replacingOccurrences(of: "fictional.supabase.co", with: "attacker.example"),
            signed.absoluteString.replacingOccurrences(of: "avatar.jpg", with: "other.jpg"),
            signed.absoluteString.replacingOccurrences(of: "?token=fictional", with: ""),
            publicURL.absoluteString,
        ] {
            let resolver = resolver { _ in URL(string: result)! }
            do {
                _ = try await resolver.resolve(publicURL)
                XCTFail("Invalid signed response accepted")
            } catch { XCTAssertTrue(error is MediaAccessError) }
        }
    }

    func testProtectedRequestsBypassNukeCachesButExternalImagesKeepThem() {
        let delegate = PrivateMediaPipelineDelegate(resolver: resolver())
        let pipeline = ImagePipeline()
        let protected = ImageRequest(url: publicURL)
        XCTAssertNil(delegate.imageCache(for: protected, pipeline: pipeline))
        XCTAssertNil(delegate.dataCache(for: protected, pipeline: pipeline))
        XCTAssertTrue(delegate.dataLoader(for: protected, pipeline: pipeline) is PrivateMediaDataLoader)
        let external = ImageRequest(url: URL(string: "https://shop.example/image.jpg")!)
        XCTAssertNotNil(delegate.imageCache(for: external, pipeline: pipeline))
        XCTAssertFalse(delegate.dataLoader(for: external, pipeline: pipeline) is PrivateMediaDataLoader)
    }
}
