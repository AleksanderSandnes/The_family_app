import Foundation
import Supabase

struct StorageMediaReference: Equatable {
    let bucket: String
    let path: String
}

enum MediaAccessError: Error {
    case invalidURL
    case missingSession
    case accountChanged
    case invalidResponse
}

/// Database URLs are stable locators. Protected media is authorized afresh for each read.
struct MediaURLResolver {
    let projectURL: URL
    let accountID: () -> String?
    let sign: (StorageMediaReference) async throws -> URL

    func reference(_ url: URL) throws -> StorageMediaReference? {
        guard url.host?.lowercased() == projectURL.host?.lowercased(),
              let source = URLComponents(url: url, resolvingAgainstBaseURL: false),
              source.percentEncodedPath.hasPrefix("/storage/v1/") else { return nil }
        guard source.scheme == projectURL.scheme,
              (source.port ?? 443) == (projectURL.port ?? 443),
              source.user == nil, source.password == nil, source.fragment == nil,
              source.percentEncodedPath.hasPrefix("/storage/v1/object/")
        else { throw MediaAccessError.invalidURL }
        let parts = String(source.percentEncodedPath.dropFirst("/storage/v1/object/".count))
            .components(separatedBy: "/")
        guard parts.count >= 3, ["public", "authenticated", "sign"].contains(parts[0])
        else { throw MediaAccessError.invalidURL }
        let bucket = try decode(parts[1])
        guard ["avatars", "group-images", "wish-images", "chat-media"].contains(bucket)
        else { throw MediaAccessError.invalidURL }
        return try StorageMediaReference(bucket: bucket, path: parts.dropFirst(2).map(decode).joined(separator: "/"))
    }

    func resolve(_ url: URL) async throws -> URL {
        guard let object = try reference(url) else { return url }
        guard let account = accountID() else { throw MediaAccessError.missingSession }
        let signed = try await sign(object)
        guard try reference(signed) == object,
              URLComponents(url: signed, resolvingAgainstBaseURL: false)?.percentEncodedPath
              .hasPrefix("/storage/v1/object/sign/") == true,
              let signedComponents = URLComponents(url: signed, resolvingAgainstBaseURL: false),
              signedComponents.queryItems?.contains(where: { $0.name == "token" && !($0.value ?? "").isEmpty }) == true
        else { throw MediaAccessError.invalidURL }
        guard accountID() == account else { throw MediaAccessError.accountChanged }
        try Task.checkCancellation()
        var result = signedComponents
        let nonce = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first {
            $0.name == "t" && ($0.value ?? "").range(of: #"^[0-9]{1,20}$"#, options: .regularExpression) != nil
        }
        if let nonce { result.queryItems = (result.queryItems ?? []) + [nonce] }
        guard let resolved = result.url else { throw MediaAccessError.invalidURL }
        return resolved
    }

    private func decode(_ raw: String) throws -> String {
        guard let value = raw.removingPercentEncoding, !value.isEmpty,
              value != ".", value != "..",
              !value.contains(where: { "/\\%".contains($0) }),
              value.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) })
        else { throw MediaAccessError.invalidURL }
        return value
    }
}

enum FamilyMedia {
    static let resolver = MediaURLResolver(
        projectURL: SupabaseClientProvider.projectURL,
        accountID: { SupabaseClientProvider.client.auth.currentSession?.user.id.uuidString },
        sign: { object in
            try await SupabaseClientProvider.client.storage.from(object.bucket)
                .createSignedURL(path: object.path, expiresIn: 300)
        }
    )

    static func data(from url: URL, timeout: TimeInterval = 15) async throws -> (Data, URLResponse) {
        try await PrivateMediaDataLoader.download(url: url, resolver: resolver, timeout: timeout)
    }
}
