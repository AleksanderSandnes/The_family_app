// Auth event access behind the FamilyRepositoryProtocol seam, so the view model is
// unit-testable with a mock. Login/register/google/completeSignIn live on the repo.
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
        let userID = try await resolveAuthenticatedAppUserID()
        session.signIn(userId: userID)
        return userID
    }

    func restoreAuthSession() async throws -> String {
        let current = try await client.auth.session
        return current.user.id.uuidString.lowercased()
    }

    func currentAuthUserID() -> String? {
        client.auth.currentSession?.user.id.uuidString.lowercased()
    }

    func resolveAuthenticatedAppUserID() async throws -> String {
        let resolver = AuthProfileResolver(
            currentIdentity: {
                guard let current = self.client.auth.currentSession else { return nil }
                return AuthIdentitySnapshot(
                    authUserID: current.user.id.uuidString.lowercased(), accessToken: current.accessToken
                )
            },
            loadAppUserID: { authID in
                let users: [UserModel] = try await self.client.from("users")
                    .select().eq("auth_id", value: authID).execute().value
                guard let user = users.first else { throw RepositoryError.profileNotFound }
                return user.id
            }
        )
        return try await resolver.resolve()
    }

    /// Initial local sessions may be expired; bootstrap must validate them first.
    /// Confirmed sign-in/refresh events and sign-out keep navigation in sync later.
    func authSessionEvents() -> AsyncStream<AuthSessionEvent> {
        let changes = client.auth.authStateChanges
        return AsyncStream { continuation in
            let task = Task {
                for await (event, current) in changes {
                    if let update = AuthSessionEvent.from(
                        event, authUserID: current?.user.id.uuidString.lowercased()
                    ) {
                        continuation.yield(update)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
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

    func sendPasswordResetEmail(email: String) async throws {
        let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        try await client.auth.resetPasswordForEmail(norm)
    }

    /// Verifies the emailed 6-digit recovery code (which signs the user in), sets the new
    /// password, and finalizes the app session so the auth gate flips.
    @discardableResult
    func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String {
        let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        try await client.auth.verifyOTP(email: norm, token: code, type: .recovery)
        try await client.auth.update(user: UserAttributes(password: newPassword))
        return try await completeSignInAfterConfirmation()
    }

    func hasAuthSession() -> Bool {
        client.auth.currentSession != nil
    }

    /// Verifies the emailed 6-digit signup code (which signs the user in) and
    /// finalizes the app session so the auth gate flips.
    @discardableResult
    func confirmSignupEmail(email: String, code: String) async throws -> String {
        let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        try await client.auth.verifyOTP(email: norm, token: code, type: .signup)
        return try await completeSignInAfterConfirmation()
    }

    func resendSignupCode(email: String) async throws {
        let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        try await client.auth.resend(email: norm, type: .signup)
    }

    func signOut() async {
        // Remove this device's push token while the auth session is still valid (RLS).
        await unregisterPushToken()
        try? await client.auth.signOut()
        invalidateUserCache()
        session.signOut()
    }
}

extension AuthSessionEvent {
    static func from(_ event: AuthChangeEvent, authUserID: String?) -> AuthSessionEvent? {
        if event == .signedOut || (event == .initialSession && authUserID == nil) {
            return .signedOut
        }
        if [.signedIn, .tokenRefreshed, .passwordRecovery, .userUpdated].contains(event), let authUserID {
            return .authenticated(authUserID)
        }
        return nil
    }
}
