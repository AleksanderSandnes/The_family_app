// The dependency seam for ViewModels. Every VM depends on this protocol (injected via
// init) rather than the concrete FamilyRepository singleton, so tests can substitute a
// MockRepository with no live Supabase backend. FamilyRepository conforms as-is.
//
// Feature CRUD that currently lives as direct `client.from(...)` calls inside VMs is being
// migrated onto this protocol (feature by feature) so those code paths become mockable too.
import Foundation

@MainActor
protocol FamilyRepositoryProtocol: AnyObject {
    // Session / prefs
    var session: SessionStore { get }
    var pendingJoinCode: String? { get }

    // Users / family reads
    func getUser(_ userId: String) async -> UserModel?
    func getFamily(familyId: String) async -> FamilyModel?
    func getFamilyMembers(familyId: String) async -> [UserModel]
    func getMyRelations(userId: String) async -> [String: String]
    func familyChanged() -> AsyncStream<Void>
    func invalidateUserCache()

    // Family lifecycle
    func createFamily(name: String, code: String, userId: String) async throws -> String
    func joinFamily(code: String, userId: String) async throws -> String
    func leaveFamily(userId: String) async
    func renameFamily(familyId: String, newName: String) async throws
    func updateFamilyPhoto(familyId: String, photoUrl: String) async throws
    func removeFamilyMember(memberId: String) async throws
    func setRelation(fromUserId: String, toUserId: String, familyId: String, relation: String) async

    /// Profile
    func updateProfile(userId: String, update: ProfileUpdate) async

    // Auth
    func login(email: String, password: String) async throws -> String
    func register(name: String, email: String, password: String, birthday: String, mobile: String) async throws
    func signInWithGoogle() async throws
    func signOut() async
    func completeSignInAfterConfirmation() async throws -> String
    func consumePendingJoinCode() -> String?
    func setPendingJoinCode(_ code: String)

    // Chat
    func getLastMessage(conversationId: String) async -> MessageModel?
    func markConversationRead(conversationId: String) async
    func sendMessage(conversationId: String, text: String) async throws
    func addReaction(messageId: String, conversationId: String, emoji: String) async throws
    func removeReaction(messageId: String) async throws

    // Settings
    func setThemeMode(_ mode: ThemeMode)
    func setNotificationsEnabled(_ enabled: Bool) async
    func setNotifyDaysBefore(_ days: Int) async
    func setLocationVisible(_ visible: Bool)
}

/// FamilyRepository already implements every method above; this just records the conformance.
extension FamilyRepository: FamilyRepositoryProtocol {}
