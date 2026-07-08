// Test double for FamilyRepositoryProtocol. Returns canned data and records calls, so
// ViewModel behaviour can be unit-tested with no live Supabase backend.
@testable import FamilyApp
import Foundation

@MainActor
final class MockRepository: FamilyRepositoryProtocol {
    let session: SessionStore
    var pendingJoinCode: String?

    // Canned reads (set by the test)
    var users: [String: UserModel] = [:]
    var families: [String: FamilyModel] = [:]
    var familyMembers: [UserModel] = []
    var relations: [String: String] = [:]
    var lastMessageByConversation: [String: MessageModel] = [:]
    var joinResult = "family-joined"
    var createResult = "family-created"
    var loginResult = "user-logged-in"
    var confirmResult = "user-confirmed"

    // Recorded writes (asserted by the test)
    private(set) var updatedProfiles: [(userId: String, update: ProfileUpdate)] = []
    private(set) var renamedFamilies: [(familyId: String, name: String)] = []
    private(set) var updatedFamilyPhotos: [(familyId: String, url: String)] = []
    private(set) var removedMembers: [String] = []
    struct RelationRecord: Equatable { let from: String
        let to: String
        let relation: String
    }

    private(set) var setRelations: [RelationRecord] = []
    private(set) var sentMessages: [(conversationId: String, text: String)] = []
    private(set) var addedReactions: [(messageId: String, emoji: String)] = []
    private(set) var removedReactions: [String] = []
    private(set) var markReadConversations: [String] = []
    private(set) var themeModes: [ThemeMode] = []
    private(set) var notificationsEnabled: [Bool] = []
    private(set) var notifyDaysBefore: [Int] = []
    private(set) var locationVisible: [Bool] = []
    private(set) var signOutCalled = false
    private(set) var googleSignInCalled = false
    private(set) var leaveFamilyCalls: [String] = []
    private(set) var registeredUsers: [String] = []
    private(set) var invalidatedCache = false

    init(session: SessionStore? = nil) {
        self.session = session ?? SessionStore(defaults: UserDefaults(suiteName: "mock-\(UUID().uuidString)")!)
    }

    /// Reads
    func getUser(_ userId: String) async -> UserModel? {
        users[userId]
    }

    func getFamily(familyId: String) async -> FamilyModel? {
        families[familyId]
    }

    func getFamilyMembers(familyId _: String) async -> [UserModel] {
        familyMembers
    }

    func getMyRelations(userId _: String) async -> [String: String] {
        relations
    }

    func getLastMessage(conversationId: String) async -> MessageModel? {
        lastMessageByConversation[conversationId]
    }

    func familyChanged() -> AsyncStream<Void> {
        AsyncStream { $0.finish() }
    }

    func invalidateUserCache() {
        invalidatedCache = true
    }

    /// Family lifecycle
    func createFamily(name: String, code _: String, userId _: String) async throws -> String {
        renamedFamilies.append((createResult, name))
        return createResult
    }

    func joinFamily(code _: String, userId _: String) async throws -> String {
        joinResult
    }

    func leaveFamily(userId: String) async {
        leaveFamilyCalls.append(userId)
    }

    func renameFamily(familyId: String, newName: String) async throws {
        renamedFamilies.append((familyId, newName))
    }

    func updateFamilyPhoto(familyId: String, photoUrl: String) async throws {
        updatedFamilyPhotos.append((familyId, photoUrl))
    }

    func removeFamilyMember(memberId: String) async throws {
        removedMembers.append(memberId)
    }

    func setRelation(fromUserId: String, toUserId: String, familyId _: String, relation: String) async {
        setRelations.append(RelationRecord(from: fromUserId, to: toUserId, relation: relation))
    }

    /// Profile
    func updateProfile(userId: String, update: ProfileUpdate) async {
        updatedProfiles.append((userId, update))
    }

    /// Auth
    func login(email _: String, password _: String) async throws -> String {
        loginResult
    }

    func register(
        name: String, email _: String, password _: String, birthday _: String, mobile _: String
    ) async throws {
        registeredUsers.append(name)
    }

    func signInWithGoogle() async throws {
        googleSignInCalled = true
    }

    func signOut() async {
        signOutCalled = true
    }

    func completeSignInAfterConfirmation() async throws -> String {
        confirmResult
    }

    func consumePendingJoinCode() -> String? {
        defer { pendingJoinCode = nil }
        return pendingJoinCode
    }

    func setPendingJoinCode(_ code: String) {
        pendingJoinCode = code
    }

    /// Chat
    func markConversationRead(conversationId: String) async {
        markReadConversations.append(conversationId)
    }

    func sendMessage(conversationId: String, text: String) async throws {
        sentMessages.append((conversationId, text))
    }

    func addReaction(messageId: String, conversationId _: String, emoji: String) async throws {
        addedReactions.append((messageId, emoji))
    }

    func removeReaction(messageId: String) async throws {
        removedReactions.append(messageId)
    }

    /// Settings
    func setThemeMode(_ mode: ThemeMode) {
        themeModes.append(mode)
    }

    func setNotificationsEnabled(_ enabled: Bool) async {
        notificationsEnabled.append(enabled)
    }

    func setNotifyDaysBefore(_ days: Int) async {
        notifyDaysBefore.append(days)
    }

    func setLocationVisible(_ visible: Bool) {
        locationVisible.append(visible)
    }

    // Birthdays
    var birthdaysResult: [BirthdayModel] = []
    private(set) var insertedBirthdays: [BirthdayModel] = []
    private(set) var updatedBirthdays: [BirthdayModel] = []
    private(set) var deletedBirthdayIds: [String] = []

    func fetchBirthdays(userId _: String, familyId _: String?) async throws -> [BirthdayModel] {
        birthdaysResult
    }

    func insertBirthday(_ birthday: BirthdayModel) async {
        insertedBirthdays.append(birthday)
    }

    func updateBirthday(id: String, name: String, date: String, icon: String, color: Int?) async {
        var b = BirthdayModel()
        b.id = id
        b.name = name
        b.date = date
        b.icon = icon
        b.color = color
        updatedBirthdays.append(b)
    }

    func deleteBirthday(id: String) async {
        deletedBirthdayIds.append(id)
    }

    // Calendar
    var calendarEventsResult: [CalendarEventModel] = []
    private(set) var insertedEvents: [CalendarEventModel] = []
    private(set) var updatedEvents: [CalendarEventModel] = []
    private(set) var deletedEventIds: [String] = []

    func fetchCalendarEvents(userId _: String, familyId _: String?) async throws -> [CalendarEventModel] {
        calendarEventsResult
    }

    func insertCalendarEvent(_ event: CalendarEventModel) async {
        insertedEvents.append(event)
    }

    func updateCalendarEvent(_ event: CalendarEventModel) async {
        updatedEvents.append(event)
    }

    func deleteCalendarEvent(id: String) async {
        deletedEventIds.append(id)
    }

    // Home
    var mealPlansResult: [MealPlanModel] = []
    var mealPlanDaysResult: [MealPlanDayModel] = []
    var homeEventsResult: [CalendarEventModel] = []
    var homeBirthdaysResult: [BirthdayModel] = []
    var shoppingListsResult: [ShoppingListModel] = []
    var uncheckedItemsResult: [ShoppingItemModel] = []
    private(set) var fetchedMealPlanDayRequests: [(mealPlanId: String, date: String)] = []
    private(set) var fetchedUncheckedListIds: [[String]] = []

    func fetchMealPlans(familyId _: String) async throws -> [MealPlanModel] {
        mealPlansResult
    }

    func fetchMealPlanDays(mealPlanId: String, date: String) async throws -> [MealPlanDayModel] {
        fetchedMealPlanDayRequests.append((mealPlanId, date))
        return mealPlanDaysResult
    }

    func fetchFamilyCalendarEvents(familyId _: String) async throws -> [CalendarEventModel] {
        homeEventsResult
    }

    func fetchFamilyBirthdays(familyId _: String) async throws -> [BirthdayModel] {
        homeBirthdaysResult
    }

    func fetchShoppingLists(familyId _: String) async throws -> [ShoppingListModel] {
        shoppingListsResult
    }

    func fetchUncheckedShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel] {
        fetchedUncheckedListIds.append(listIds)
        guard !listIds.isEmpty else { return [] }
        return uncheckedItemsResult
    }
}
