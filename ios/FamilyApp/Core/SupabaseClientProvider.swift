// Singleton Supabase client — the iOS twin of SupabaseManager.kt.
// Same project, same anon key, same PKCE deep-link flow (familyapp://auth) and the
// same Realtime heartbeat tuning the Android client needed on mobile networks.
import Foundation
import Supabase

enum SupabaseClientProvider {
    static let deepLinkScheme = "familyapp"
    static let deepLinkHost = "auth"
    static let authRedirectURL = URL(string: "familyapp://auth")!

    static let client: SupabaseClient = {
        guard let urlString = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String,
              let key = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_ANON_KEY") as? String,
              let url = URL(string: urlString),
              urlString.hasPrefix("https://"),
              !urlString.contains("your-project"),
              !key.isEmpty, !key.contains("your-anon-key")
        else {
            fatalError(
                "Supabase secrets missing/placeholder — create ios/Config/Secrets.xcconfig " +
                    "from Secrets.example.xcconfig, then re-run xcodegen generate."
            )
        }
        return SupabaseClient(
            supabaseURL: url,
            supabaseKey: key,
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(
                    redirectToURL: authRedirectURL,
                    flowType: .pkce
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
