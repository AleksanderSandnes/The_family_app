// Auth event access — moved out of AuthViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock (no live client). The
// login/register/google/completeSignIn methods already live on the repo.
import Foundation
import Supabase

extension FamilyRepository {
    /// Emits once for every `.signedIn` auth-state change — used to finalize external
    /// (Google OAuth) sign-in once the redirect lands. Wraps the client's authStateChanges
    /// so the VM never touches SupabaseClientProvider directly.
    func authSignedInEvents() -> AsyncStream<Void> {
        let changes = client.auth.authStateChanges
        return AsyncStream { continuation in
            let task = Task {
                for await (event, _) in changes where event == .signedIn {
                    continuation.yield(())
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
