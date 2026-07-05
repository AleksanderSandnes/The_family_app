// Auth gate — the iOS twin of RootViewModel.kt. Emits Loading → SignedOut → SignedIn
// based on the stored app user id (public.users.id, NOT the auth UUID).
import Foundation
import Observation

enum AuthGate {
    case loading
    case signedOut
    case signedIn
}

@Observable
@MainActor
final class RootViewModel {
    private let store = SessionStore.shared
    private var bootstrapped = false

    var gate: AuthGate {
        if !bootstrapped { return .loading }
        return store.currentUserId != nil ? .signedIn : .signedOut
    }

    /// Restores the Supabase auth session (supabase-swift persists it in the keychain),
    /// then opens the gate. The stored app user id is the source of truth — same as
    /// Android's DataStore flow.
    func bootstrap() async {
        guard !bootstrapped else { return }
        // Touch the session so token refresh happens before the first query.
        _ = try? await SupabaseClientProvider.client.auth.session
        bootstrapped = true
        if store.currentUserId != nil {
            await FamilyRepository.shared.touchLastActive()
        }
    }
}
