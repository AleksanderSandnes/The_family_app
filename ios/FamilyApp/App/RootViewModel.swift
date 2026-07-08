// Auth gate — the iOS twin of RootViewModel.kt. Emits Loading → SignedOut → SignedIn
// based on the stored app user id (public.users.id, NOT the auth UUID).
import Foundation
import Observation

enum AuthGate {
    case loading
    case signedOut
    case needsPermissions
    case signedIn
}

@Observable
@MainActor
final class RootViewModel {
    private let store = SessionStore.shared
    private let repo: FamilyRepositoryProtocol
    private var bootstrapped = false
    private var didSyncAfterSignIn = false

    init(repo: FamilyRepositoryProtocol = FamilyRepository.shared) {
        self.repo = repo
    }

    var gate: AuthGate {
        if !bootstrapped { return .loading }
        if store.currentUserId == nil { return .signedOut }
        if !store.permissionsRequested { return .needsPermissions }
        return .signedIn
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
            await repo.touchLastActive()
        }
    }

    /// Once fully signed in, register this device's push token and mirror the local
    /// notification settings to the server (Android RootViewModel parity).
    func onSignedIn() async {
        guard !didSyncAfterSignIn else { return }
        didSyncAfterSignIn = true
        await repo.syncPushToken()
        await repo.syncNotificationPrefsToServer()
    }

    func completePermissionsOnboarding() {
        store.setPermissionsRequested()
    }
}
