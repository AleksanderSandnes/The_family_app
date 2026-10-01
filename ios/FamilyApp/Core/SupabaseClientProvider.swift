// Singleton Supabase client: PKCE deep-link flow (familyapp://auth) and Realtime heartbeat
// tuning for mobile networks.
import Foundation
import Supabase

enum SupabaseClientProvider {
    static let deepLinkScheme = "familyapp"
    static let deepLinkHost = "auth"
    static let authRedirectURL = URL(string: "familyapp://auth")!

    static let projectURL: URL = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String,
              let url = URL(string: value), url.scheme == "https", url.host != nil,
              !value.contains("your-project")
        else { fatalError("Supabase URL missing — configure ios/Config/Secrets.xcconfig") }
        return url
    }()

    /// Test seam: unit tests install a client backed by a stubbed transport so repository and
    /// service code runs against fictional responses. The app never sets it.
    static var testOverride: SupabaseClient?

    static var client: SupabaseClient {
        testOverride ?? liveClient
    }

    private static let liveClient: SupabaseClient = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_ANON_KEY") as? String,
              !key.isEmpty, !key.contains("your-anon-key")
        else {
            fatalError(
                "Supabase secrets missing/placeholder — create ios/Config/Secrets.xcconfig " +
                    "from Secrets.example.xcconfig, then re-run xcodegen generate."
            )
        }
        return SupabaseClient(
            supabaseURL: projectURL,
            supabaseKey: key,
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(
                    redirectToURL: authRedirectURL,
                    flowType: .pkce,
                    // Emit the stored session immediately as the initial session (opt-in to
                    // supabase-swift's next-major behavior; silences the runtime warning). Safe
                    // here: the auth gate doesn't trust `.initialSession` — RootViewModel.bootstrap()
                    // validates via `auth.session` and resolves its matching app profile, and an
                    // expired token is refreshed in the background by the library.
                    emitLocalSessionAsInitialSession: true
                ),
                realtime: RealtimeClientOptions(
                    // Default 15s is too short on mobile — the server sometimes takes >15s
                    // to ack, causing spurious heartbeat timeouts. 25s matches Android.
                    heartbeatInterval: 25,
                    // Reconnect quickly so the gap where events can be missed stays small.
                    reconnectDelay: 3
                )
            )
        )
    }()
}
