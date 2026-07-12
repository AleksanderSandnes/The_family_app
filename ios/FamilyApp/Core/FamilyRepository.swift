// Application-scoped repository singleton. Central API for auth, family management, user
// profile and settings.
//
// Dual-ID rule:
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
    /// Internal (not private) so the `+Settings` / `+Chat` extensions in sibling files
    /// can reach it.
    var client: SupabaseClient {
        SupabaseClientProvider.client
    }

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

    func setPendingJoinCode(_ code: String) {
        pendingJoinCode = code
    }

    func consumePendingJoinCode() -> String? {
        defer { pendingJoinCode = nil }
        return pendingJoinCode
    }

    // MARK: - Presence

    /// Bumps the current user's last_active_at — called when the app foregrounds.
    func touchLastActive() async {
        guard let userId = session.currentUserId else { return }
        _ = try? await client.from("users")
            .update(["last_active_at": AnyJSON.string(isoNow())])
            .eq("id", value: userId)
            .execute()
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
        _ = try? await client.from("device_push_tokens")
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
        let token: String? = if let lastPushToken {
            lastPushToken
        } else {
            await pushTokenProvider?()
        }
        guard let token else { return }
        _ = try? await client.from("device_push_tokens")
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
        let rows: [UserModel] = await (try? client.from("users")
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
        await (try? client.from("users")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
    }

    // MARK: - Family relations (directional, relative to each viewer)

    /// My relations to other members, keyed by the other member's id (toUserId → relation).
    func getMyRelations(userId: String) async -> [String: String] {
        let rows: [FamilyRelationModel] = await (try? client.from("family_relations")
            .select()
            .eq("from_user_id", value: userId)
            .execute()
            .value) ?? []
        return Dictionary(rows.map { ($0.toUserId, $0.relation) }, uniquingKeysWith: { first, _ in first })
    }

    /// Sets (or clears) my relation to another family member. Empty string removes it.
    func setRelation(fromUserId: String, toUserId: String, familyId: String, relation: String) async {
        let trimmed = relation.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty {
            _ = try? await client.from("family_relations").delete()
                .eq("from_user_id", value: fromUserId)
                .eq("to_user_id", value: toUserId)
                .execute()
            return
        }
        _ = try? await client.from("family_relations")
            .upsert([
                "family_id": AnyJSON.string(familyId),
                "from_user_id": .string(fromUserId),
                "to_user_id": .string(toUserId),
                "relation": .string(trimmed),
            ], onConflict: "from_user_id,to_user_id")
            .execute()
    }

    func getFamily(familyId: String) async -> FamilyModel? {
        let rows: [FamilyModel] = await (try? client.from("families")
            .select()
            .eq("id", value: familyId)
            .execute()
            .value) ?? []
        return rows.first
    }

    /// Whether `userId` is their family's admin. Drives the creator-or-admin gating of
    /// destructive actions (mirrors the `i_am_family_admin()` RLS helper).
    func isFamilyAdmin(userId: String) async -> Bool {
        guard let familyId = await getUser(userId)?.familyId else { return false }
        return await getFamily(familyId: familyId)?.adminId == userId
    }

    // MARK: - Auth

    // Auth methods (register / completeSignInAfterConfirmation / signInWithGoogle / login /
    // signOut) and authSignedInEvents live in FamilyRepository+Auth.swift.

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
        // Join is validated and family_id is set inside the join_family() RPC
        // (SECURITY DEFINER). Direct users.family_id writes are blocked by an RLS
        // trigger, so a user can't self-join an arbitrary family. A nil result means
        // the code didn't match any family.
        let familyId: String? = try await client
            .rpc("join_family", params: ["p_code": code.trimmingCharacters(in: .whitespacesAndNewlines)])
            .execute()
            .value
        guard let familyId else { throw RepositoryError.joinCodeIncorrect }
        await syncUserBirthday(userId: userId, familyId: familyId)
        invalidateUserCache()
        emitFamilyChanged()
        return familyId
    }

    /// Creates OR updates the user's own shared birthday event, named "{name}'s birthday".
    /// Called on family create/join and whenever the birthday is changed in the profile, so
    /// the family always sees an up-to-date entry. Repeats yearly — the birthday screen always
    /// projects the next occurrence from this single stored date.
    private func syncUserBirthday(userId: String, familyId: String) async {
        guard let user = await getUser(userId), !user.birthday.isEmpty else { return }
        let title = "\(user.name)'s birthday"
        do {
            let existing: [BirthdayModel] = try await client.from("birthdays")
                .select()
                .eq("user_id", value: userId)
                .eq("family_id", value: familyId)
                .execute()
                .value
            if let row = existing.first {
                try await client.from("birthdays")
                    .update([
                        "name": AnyJSON.string(title),
                        "date": .string(user.birthday),
                    ])
                    .eq("id", value: row.id)
                    .execute()
            } else {
                try await client.from("birthdays")
                    .insert([
                        "name": AnyJSON.string(title),
                        "date": .string(user.birthday),
                        "family_id": .string(familyId),
                        "user_id": .string(userId),
                        "made_by_user_id": .string(userId),
                    ])
                    .execute()
            }
        } catch {
            // Best-effort.
        }
    }

    func leaveFamily(userId: String) async {
        guard let user = await getUser(userId) else { return }
        guard let familyId = user.familyId else {
            _ = try? await client.from("users")
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
            // Only emit on success.
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
        _ = try? await client.from("users")
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
        // Keep the shared birthday event in sync with the just-saved birthday/name.
        if let user = await getUser(userId), let familyId = user.familyId {
            await syncUserBirthday(userId: userId, familyId: familyId)
        }
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

    // MARK: - Avatar palette (parity with Android)

    /// ARGB avatar palette (matches Android so a name maps to the same color).
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
    static func javaHashCode(_ text: String) -> Int32 {
        var hash: Int32 = 0
        for unit in text.utf16 {
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
