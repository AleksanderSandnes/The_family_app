// Application-scoped repository singleton — the iOS twin of FamilyRepository.kt.
// Central API for auth, family management, user profile and settings. Feature CRUD that
// lives in Android ViewModels talks straight to `SupabaseClientProvider.client` here too.
//
// Dual-ID rule (verbatim from Android):
//   - `SessionStore.currentUserId`  = public.users.id  → all foreign keys
//   - `client.auth.currentSession?.user.id` = auth_id  → RLS checks + Storage paths
import Foundation
import Supabase

/// Editable profile fields for `updateProfile`, grouped into one parameter.
struct ProfileUpdate {
    var name: String
    var email: String
    var birthday: String
    var mobile: String
    var avatarUrl: String?
}

@MainActor
final class FamilyRepository {
    static let shared = FamilyRepository()

    let session = SessionStore.shared
    private var client: SupabaseClient { SupabaseClientProvider.client }

    private init() {}

    // MARK: - familyChanged (Android: MutableSharedFlow)

    private var familyChangedContinuations: [UUID: AsyncStream<Void>.Continuation] = [:]

    /// Emits after createFamily / joinFamily / leaveFamily (and member removal / photo change).
    /// ViewModels that scope queries to familyId should observe this or refresh on foreground.
    func familyChanged() -> AsyncStream<Void> {
        AsyncStream { continuation in
            let id = UUID()
            familyChangedContinuations[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.familyChangedContinuations[id] = nil }
            }
        }
    }

    private func emitFamilyChanged() {
        for continuation in familyChangedContinuations.values {
            continuation.yield(())
        }
    }

    // MARK: - Pending join code (familyapp://join?code=...)

    /// Family invite code captured from a deep link, consumed by the Family screen
    /// once the user is signed in.
    private(set) var pendingJoinCode: String?

    func setPendingJoinCode(_ code: String) { pendingJoinCode = code }

    func consumePendingJoinCode() -> String? {
        defer { pendingJoinCode = nil }
        return pendingJoinCode
    }

    // MARK: - Presence

    /// Bumps the current user's last_active_at — called when the app foregrounds.
    func touchLastActive() async {
        guard let userId = session.currentUserId else { return }
        try? await client.from("users")
            .update(["last_active_at": AnyJSON.string(isoNow())])
            .eq("id", value: userId)
            .execute()
    }

    // MARK: - Settings mirrors (server copy of notification prefs)

    func setThemeMode(_ mode: ThemeMode) { session.setThemeMode(mode) }

    func setNotificationsEnabled(_ enabled: Bool) async {
        session.setNotificationsEnabled(enabled)
        await updateUserNotificationPrefs(enabled: enabled, days: nil)
    }

    func setNotifyDaysBefore(_ days: Int) async {
        session.setNotifyDaysBefore(days)
        await updateUserNotificationPrefs(enabled: nil, days: days)
    }

    func setLocationVisible(_ visible: Bool) { session.setLocationVisible(visible) }

    func setPermissionsRequested() { session.setPermissionsRequested() }

    /// Mirrors the client notification settings onto the user's row so the server-side
    /// daily-reminders function can honour them. UserDefaults stays the UI source of truth.
    private func updateUserNotificationPrefs(enabled: Bool?, days: Int?) async {
        guard let userId = session.currentUserId else { return }
        var values: [String: AnyJSON] = [:]
        if let enabled { values["notifications_enabled"] = .bool(enabled) }
        if let days { values["notify_days_before"] = .integer(days) }
        guard !values.isEmpty else { return }
        do {
            try await client.from("users").update(values).eq("id", value: userId).execute()
            invalidateUserCache()
        } catch {
            // Best-effort mirror, same as Android's runCatching.
        }
    }

    /// Pushes the current stored notification settings to the server once after sign-in.
    func syncNotificationPrefsToServer() async {
        await updateUserNotificationPrefs(
            enabled: session.notificationsEnabled,
            days: session.notifyDaysBefore
        )
    }

    // MARK: - Push tokens (FCM) — wired up fully in the push phase

    private var lastPushToken: String?

    /// Set by the AppDelegate once FirebaseMessaging is configured (push phase).
    var pushTokenProvider: (() async -> String?)?

    /// Fetches the current FCM token and stores it for the signed-in user. Safe to call
    /// repeatedly (upsert) and a no-op when Firebase isn't configured or nobody is signed in.
    func syncPushToken() async {
        guard let token = await pushTokenProvider?() else { return }
        await registerPushToken(token)
    }

    /// Upserts this device's push token for the current user with platform='ios'.
    func registerPushToken(_ token: String) async {
        guard let userId = session.currentUserId else { return }
        lastPushToken = token
        try? await client.from("device_push_tokens")
            .upsert(
                [
                    "user_id": AnyJSON.string(userId),
                    "token": .string(token),
                    "platform": .string("ios"),
                    "updated_at": .string(isoNow()),
                ],
                onConflict: "token"
            )
            .execute()
    }

    /// Removes this device's push token. Must run while still authenticated (RLS), so it
    /// is called from `signOut()` before the auth session is torn down.
    func unregisterPushToken() async {
        let token: String?
        if let lastPushToken {
            token = lastPushToken
        } else {
            token = await pushTokenProvider?()
        }
        guard let token else { return }
        try? await client.from("device_push_tokens")
            .delete()
            .eq("token", value: token)
            .execute()
        lastPushToken = nil
    }

    // MARK: - User cache

    private var cachedUser: UserModel?
    private var cachedUserId: String?

    func invalidateUserCache() {
        cachedUser = nil
        cachedUserId = nil
    }

    func getUser(_ userId: String) async -> UserModel? {
        if userId == cachedUserId, let cachedUser { return cachedUser }
        let rows: [UserModel] = (try? await client.from("users")
            .select()
            .eq("id", value: userId)
            .execute()
            .value) ?? []
        let fetched = rows.first
        // Only cache a successful, non-nil fetch. Caching nil (e.g. on a transient
        // network/RLS failure) would poison the cache for the whole session — every
        // feature would then read the user as "no family" and create unscoped rows.
        if let fetched {
            cachedUser = fetched
            cachedUserId = userId
        }
        return fetched
    }

    func getFamilyMembers(familyId: String) async -> [UserModel] {
        (try? await client.from("users")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
    }

    func getFamily(familyId: String) async -> FamilyModel? {
        let rows: [FamilyModel] = (try? await client.from("families")
            .select()
            .eq("id", value: familyId)
            .execute()
            .value) ?? []
        return rows.first
    }

    // MARK: - Auth

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

    // MARK: - Family

    func createFamily(name: String, code: String, userId: String) async throws -> String {
        let family: FamilyModel = try await client.from("families")
            .insert([
                "name": AnyJSON.string(name.trimmingCharacters(in: .whitespacesAndNewlines)),
                "join_code": .string(code),
                "admin_id": .string(userId),
            ])
            .select()
            .single()
            .execute()
            .value
        try await client.from("users")
            .update(["family_id": AnyJSON.string(family.id)])
            .eq("id", value: userId)
            .execute()
        await syncUserBirthday(userId: userId, familyId: family.id)
        invalidateUserCache()
        emitFamilyChanged()
        return family.id
    }

    func joinFamily(code: String, userId: String) async throws -> String {
        let families: [FamilyModel] = try await client.from("families")
            .select()
            .eq("join_code", value: code.trimmingCharacters(in: .whitespacesAndNewlines))
            .execute()
            .value
        guard let family = families.first else { throw RepositoryError.joinCodeIncorrect }
        try await client.from("users")
            .update(["family_id": AnyJSON.string(family.id)])
            .eq("id", value: userId)
            .execute()
        await syncUserBirthday(userId: userId, familyId: family.id)
        invalidateUserCache()
        emitFamilyChanged()
        return family.id
    }

    /// Auto-creates the user's own birthday entry when creating/joining a family.
    private func syncUserBirthday(userId: String, familyId: String) async {
        guard let user = await getUser(userId), !user.birthday.isEmpty else { return }
        do {
            let existing: [BirthdayModel] = try await client.from("birthdays")
                .select()
                .eq("user_id", value: userId)
                .eq("family_id", value: familyId)
                .execute()
                .value
            guard existing.isEmpty else { return }
            try await client.from("birthdays")
                .insert([
                    "name": AnyJSON.string(user.name),
                    "date": .string(user.birthday),
                    "family_id": .string(familyId),
                    "user_id": .string(userId),
                    "made_by_user_id": .string(userId),
                ])
                .execute()
        } catch {
            // Best-effort, same as Android.
        }
    }

    func leaveFamily(userId: String) async {
        guard let user = await getUser(userId) else { return }
        guard let familyId = user.familyId else {
            try? await client.from("users")
                .update(["family_id": AnyJSON.null])
                .eq("id", value: userId)
                .execute()
            invalidateUserCache()
            emitFamilyChanged()
            return
        }
        do {
            try await cleanupConversationsForLeavingUser(userId: userId)

            // Delete user's calendar events in this family.
            try await client.from("calendar_events").delete()
                .eq("user_id", value: userId)
                .eq("family_id", value: familyId)
                .execute()

            // Delete birthdays created by this user in this family.
            try await client.from("birthdays").delete()
                .eq("made_by_user_id", value: userId)
                .eq("family_id", value: familyId)
                .execute()

            // Delete user's shopping lists in this family (items cascade).
            try await client.from("shopping_lists").delete()
                .eq("owner_user_id", value: userId)
                .eq("family_id", value: familyId)
                .execute()

            // Clear family membership.
            try await client.from("users")
                .update(["family_id": AnyJSON.null])
                .eq("id", value: userId)
                .execute()

            invalidateUserCache()
            emitFamilyChanged()
        } catch {
            // Mirror of Android's runCatching: only emit on success.
        }
    }

    /// Deletes conversations where the leaving user was the sole participant and
    /// removes their participant rows from any group conversations.
    private func cleanupConversationsForLeavingUser(userId: String) async throws {
        let myRows: [ConversationParticipantModel] = try await client
            .from("conversation_participants")
            .select()
            .eq("user_id", value: userId)
            .execute()
            .value
        let myConversationIds = myRows.map(\.conversationId)
        guard !myConversationIds.isEmpty else { return }

        let allRows: [ConversationParticipantModel] = try await client
            .from("conversation_participants")
            .select()
            .in("conversation_id", values: myConversationIds)
            .execute()
            .value
        let soloIds = Dictionary(grouping: allRows, by: \.conversationId)
            .filter { $0.value.count == 1 }
            .map(\.key)
        if !soloIds.isEmpty {
            try await client.from("conversations").delete()
                .in("id", values: soloIds)
                .execute()
        }
        try await client.from("conversation_participants").delete()
            .eq("user_id", value: userId)
            .execute()
    }

    func updateProfile(userId: String, update: ProfileUpdate) async {
        try? await client.from("users")
            .update([
                "name": AnyJSON.string(update.name),
                "email": .string(update.email),
                "birthday": .string(update.birthday),
                "mobile": .string(update.mobile),
                "avatar_url": update.avatarUrl.map { .string($0) } ?? .null,
            ])
            .eq("id", value: userId)
            .execute()
        invalidateUserCache()
    }

    func removeFamilyMember(memberId: String) async throws {
        try await client.from("users")
            .update(["family_id": AnyJSON.null])
            .eq("id", value: memberId)
            .execute()
        invalidateUserCache()
        emitFamilyChanged()
    }

    func renameFamily(familyId: String, newName: String) async throws {
        try await client.from("families")
            .update(["name": AnyJSON.string(newName.trimmingCharacters(in: .whitespacesAndNewlines))])
            .eq("id", value: familyId)
            .execute()
    }

    func updateFamilyPhoto(familyId: String, photoUrl: String) async throws {
        try await client.from("families")
            .update(["photo_url": AnyJSON.string(photoUrl)])
            .eq("id", value: familyId)
            .execute()
        emitFamilyChanged()
    }

    // MARK: - Chat helpers shared across screens

    func getLastMessage(conversationId: String) async -> MessageModel? {
        let rows: [MessageModel] = (try? await client.from("messages")
            .select()
            .eq("conversation_id", value: conversationId)
            .order("sent_at", ascending: false)
            .limit(1)
            .execute()
            .value) ?? []
        return rows.first
    }

    func markConversationRead(conversationId: String) async {
        guard let userId = session.currentUserId else { return }
        try? await client.from("conversation_participants")
            .update(["last_read_at": AnyJSON.string(isoNow())])
            .eq("conversation_id", value: conversationId)
            .eq("user_id", value: userId)
            .execute()
    }

    func sendMessage(conversationId: String, text: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("messages")
            .insert([
                "conversation_id": AnyJSON.string(conversationId),
                "user_from": .string(userId),
                "text": .string(text),
                "message_type": .string("text"),
            ])
            .execute()
    }

    func addReaction(messageId: String, conversationId: String, emoji: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("message_reactions")
            .upsert([
                "message_id": AnyJSON.string(messageId),
                "conversation_id": .string(conversationId),
                "user_id": .string(userId),
                "emoji": .string(emoji),
            ])
            .execute()
    }

    func removeReaction(messageId: String) async throws {
        guard let userId = session.currentUserId else { throw RepositoryError.notAuthenticated }
        try await client.from("message_reactions")
            .delete()
            .eq("message_id", value: messageId)
            .eq("user_id", value: userId)
            .execute()
    }

    // MARK: - Avatar palette (parity with Android)

    /// Same ARGB palette as FamilyRepository.kt.
    static let avatarColors: [Int] = [
        Int(bitPattern: 0xFF6366F1),
        Int(bitPattern: 0xFFEC4899),
        Int(bitPattern: 0xFF14B8A6),
        Int(bitPattern: 0xFFF59E0B),
        Int(bitPattern: 0xFF8B5CF6),
        Int(bitPattern: 0xFF06B6D4),
        Int(bitPattern: 0xFFEF4444),
        Int(bitPattern: 0xFF10B981),
    ].map { Int(Int32(truncatingIfNeeded: $0)) }

    /// Java String.hashCode — matches Kotlin so both platforms pick the same avatar
    /// color for the same name.
    static func javaHashCode(_ s: String) -> Int32 {
        var hash: Int32 = 0
        for unit in s.utf16 {
            hash = 31 &* hash &+ Int32(unit)
        }
        return hash
    }

    static func palette(_ seed: String) -> Int {
        let index = Int(javaHashCode(seed) & Int32.max) % avatarColors.count
        return avatarColors[index]
    }
}

enum RepositoryError: LocalizedError {
    case notAuthenticated
    case profileNotFound
    case joinCodeIncorrect

    var errorDescription: String? {
        switch self {
        case .notAuthenticated: "Not signed in."
        case .profileNotFound: "User profile not found."
        case .joinCodeIncorrect: "Join code is incorrect."
        }
    }
}

/// ISO-8601 timestamp with fractional seconds — matches java.time.Instant.now().toString().
func isoNow() -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter.string(from: Date())
}
