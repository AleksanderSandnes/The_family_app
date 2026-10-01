@testable import FamilyApp

// A Supabase client whose every HTTP call is answered in-process by `StubTransport`, so the
// real FamilyRepository / StorageService code runs against fictional responses. Install it
// with `StubSupabase.install()` and remove it in tearDown with `StubSupabase.uninstall()`.
import Foundation
import Supabase

/// One HTTP call the code under test made.
struct RecordedRequest {
    let method: String
    let path: String
    let query: String
    let headers: [String: String]
    let body: Data?

    /// The body decoded as a JSON object (insert/update payloads); empty when absent.
    var jsonObject: [String: Any] {
        guard let body, let object = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return [:]
        }
        return object
    }

    /// `true` when the PostgREST query string carries `column=op.value`.
    func hasFilter(_ column: String, _ filter: String) -> Bool {
        let decoded = query.removingPercentEncoding ?? query
        return decoded.split(separator: "&").contains { $0 == "\(column)=\(filter)" }
    }
}

/// URLProtocol that serves canned responses keyed by method + path and records requests.
class StubTransport: URLProtocol, @unchecked Sendable {
    private struct Route {
        let method: String?
        let path: String
        let status: Int
        let body: Data
        let headers: [String: String]
    }

    private static let lock = NSLock()
    private static var routes: [Route] = []
    private static var log: [RecordedRequest] = []

    static func reset() {
        lock.lock()
        routes = []
        log = []
        lock.unlock()
    }

    static var requests: [RecordedRequest] {
        lock.lock()
        defer { lock.unlock() }
        return log
    }

    /// Requests whose path ends with `suffix` (e.g. "/rest/v1/users"), optionally by method.
    static func requests(to suffix: String, method: String? = nil) -> [RecordedRequest] {
        requests.filter { $0.path.hasSuffix(suffix) && (method == nil || $0.method == method) }
    }

    /// Registers a response. Later registrations win, so tests can override defaults.
    static func respond(
        _ method: String? = nil,
        _ path: String,
        status: Int = 200,
        body: Data = Data("[]".utf8),
        headers: [String: String] = [:]
    ) {
        lock.lock()
        routes.insert(Route(method: method, path: path, status: status, body: body, headers: headers), at: 0)
        lock.unlock()
    }

    /// Registers a JSON-encoded model (or array of models) for a REST table read.
    static func respond(table: String, method: String = "GET", with value: some Encodable) {
        respond(method, "/rest/v1/\(table)", body: StubJSON.encode(value))
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let url = request.url ?? URL(fileURLWithPath: "/")
        let method = request.httpMethod ?? "GET"
        let recorded = RecordedRequest(
            method: method,
            path: url.path,
            query: url.query ?? "",
            headers: request.allHTTPHeaderFields ?? [:],
            body: request.httpBody ?? Self.drain(request.httpBodyStream)
        )
        Self.lock.lock()
        Self.log.append(recorded)
        let route = Self.routes.first { route in
            (route.method == nil || route.method == method) && url.path.hasSuffix(route.path)
        }
        Self.lock.unlock()

        let status = route?.status ?? Self.defaultStatus(for: method)
        let body = route?.body ?? Self.defaultBody(for: method, path: url.path)
        var headers = ["Content-Type": "application/json"]
        headers.merge(route?.headers ?? [:]) { _, new in new }
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)
        if let response {
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        }
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func defaultStatus(for method: String) -> Int {
        method == "GET" || method == "HEAD" ? 200 : 201
    }

    private static func defaultBody(for method: String, path: String) -> Data {
        if path.contains("/storage/v1/object") {
            // Uploads decode {Key, Id}; removals decode the list of removed objects.
            return method == "DELETE" ? Data("[]".utf8) : StubJSON.literal([
                "Key": String(path.split(separator: "/").suffix(3).joined(separator: "/")),
                "Id": "00000000-0000-4000-8000-0000000b0001",
            ])
        }
        return method == "GET" ? Data("[]".utf8) : Data()
    }

    private static func drain(_ stream: InputStream?) -> Data? {
        guard let stream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: buffer.count)
            guard read > 0 else { break }
            data.append(buffer, count: read)
        }
        return data
    }
}

enum StubJSON {
    static func encode(_ value: some Encodable) -> Data {
        (try? JSONEncoder().encode(value)) ?? Data("null".utf8)
    }

    /// A JSON scalar/array/object body built from a literal Swift value.
    static func literal(_ value: Any) -> Data {
        if let string = value as? String {
            return encode(string)
        }
        return (try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])) ?? Data()
    }
}

/// Keeps the stubbed client's auth session in memory (never the Keychain).
final class InMemoryAuthStorage: AuthLocalStorage, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String: Data] = [:]

    func store(key: String, value: Data) throws {
        lock.lock()
        values[key] = value
        lock.unlock()
    }

    func retrieve(key: String) throws -> Data? {
        lock.lock()
        defer { lock.unlock() }
        return values[key]
    }

    func remove(key: String) throws {
        lock.lock()
        values[key] = nil
        lock.unlock()
    }
}

@MainActor
enum StubSupabase {
    static let projectURL = URL(string: "https://fictional.supabase.co")!
    /// The fictional signed-in Auth user (auth_id) used by `signIn()`.
    static let authUserID = "00000000-0000-4000-8000-0000000a0001"

    /// Installs a fresh stubbed client and clears recorded traffic.
    @discardableResult
    static func install() -> SupabaseClient {
        StubTransport.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.protocolClasses = [StubTransport.self]
        let client = SupabaseClient(
            supabaseURL: projectURL,
            supabaseKey: "fictional-anon-key",
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(
                    storage: InMemoryAuthStorage(),
                    autoRefreshToken: false,
                    emitLocalSessionAsInitialSession: true
                ),
                global: SupabaseClientOptions.GlobalOptions(session: URLSession(configuration: configuration))
            )
        )
        SupabaseClientProvider.testOverride = client
        FamilyRepository.shared.invalidateUserCache()
        return client
    }

    static func uninstall() {
        SupabaseClientProvider.testOverride = nil
        FamilyRepository.shared.invalidateUserCache()
        SessionStore.shared.signOut()
        StubTransport.reset()
    }

    /// A GoTrue session payload for the fictional user.
    static func sessionJSON(email: String = "emma@example.com") -> Data {
        let expiresAt = Int(Date().timeIntervalSince1970) + 3600
        let user: [String: Any] = [
            "id": authUserID,
            "aud": "authenticated",
            "role": "authenticated",
            "email": email,
            "app_metadata": ["provider": "email"],
            "user_metadata": [String: Any](),
            "created_at": "2026-01-01T00:00:00Z",
            "updated_at": "2026-01-01T00:00:00Z",
        ]
        return StubJSON.literal([
            "access_token": "fictional-access-token",
            "token_type": "bearer",
            "expires_in": 3600,
            "expires_at": expiresAt,
            "refresh_token": "fictional-refresh-token",
            "user": user,
        ])
    }

    /// Serves a session for password sign-in / OTP verification and the user lookup.
    static func serveAuth(appUserID: String = "user-emma") {
        StubTransport.respond("POST", "/auth/v1/token", body: sessionJSON())
        StubTransport.respond("POST", "/auth/v1/verify", body: sessionJSON())
        StubTransport.respond("PUT", "/auth/v1/user", body: userJSON())
        StubTransport.respond("GET", "/auth/v1/user", body: userJSON())
        StubTransport.respond(
            table: "users",
            with: [UserModel(id: appUserID, authId: authUserID, name: "Emma Nordmann", email: "emma@example.com")]
        )
    }

    /// Signs the stubbed client in (real GoTrue request/response round-trip) and the app session.
    static func signIn(appUserID: String = "user-emma") async throws {
        serveAuth(appUserID: appUserID)
        try await SupabaseClientProvider.client.auth.signIn(email: "emma@example.com", password: "fictional-pass")
        SessionStore.shared.signIn(userId: appUserID)
    }

    private static func userJSON() -> Data {
        StubJSON.literal([
            "id": authUserID,
            "aud": "authenticated",
            "role": "authenticated",
            "email": "emma@example.com",
            "app_metadata": ["provider": "email"],
            "user_metadata": [String: Any](),
            "created_at": "2026-01-01T00:00:00Z",
            "updated_at": "2026-01-01T00:00:00Z",
        ])
    }
}
