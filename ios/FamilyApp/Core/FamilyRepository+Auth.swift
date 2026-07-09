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

    func register(
        name: String,
        email: String,
        password: String,
        birthday: String,
        mobile: String
    ) async throws {
        let emailNorm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        // Clear any stale session before signing up to avoid an FK from the wrong auth_id.
        try? await client.auth.signOut()
        try await client.auth.signUp(
            email: emailNorm,
            password: password,
            data: [
                "full_name": .string(name.trimmingCharacters(in: .whitespacesAndNewlines)),
                "phone": .string(mobile),
                "birthday": .string(birthday),
                "avatar_color": .integer(Self.palette(name)),
            ],
            redirectTo: SupabaseClientProvider.authRedirectURL
        )
        // The public.users profile row is created by the on_auth_user_created trigger —
        // NEVER insert into public.users manually after signup.
    }

    /// After email confirmation (or OAuth): resolves the app user id (public.users.id)
    /// from the auth session's auth_id and persists it. Returns the app user id.
    @discardableResult
    func completeSignInAfterConfirmation() async throws -> String {
        guard let authId = client.auth.currentSession?.user.id.uuidString.lowercased() else {
            throw RepositoryError.notAuthenticated
        }
        let users: [UserModel] = try await client.from("users")
            .select()
            .eq("auth_id", value: authId)
            .execute()
            .value
        guard let user = users.first else { throw RepositoryError.profileNotFound }
        session.signIn(userId: user.id)
        return user.id
    }

    /// Starts the browser-based Google OAuth flow (ASWebAuthenticationSession).
    /// Completion arrives via the familyapp://auth deep link.
    func signInWithGoogle() async throws {
        try await client.auth.signInWithOAuth(
            provider: .google,
            redirectTo: SupabaseClientProvider.authRedirectURL
        )
    }

    @discardableResult
    func login(email: String, password: String) async throws -> String {
        let emailNorm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        try await client.auth.signIn(email: emailNorm, password: password)
        return try await completeSignInAfterConfirmation()
    }

    func signOut() async {
        // Remove this device's push token while the auth session is still valid (RLS).
        await unregisterPushToken()
        try? await client.auth.signOut()
        invalidateUserCache()
        session.signOut()
    }
}
