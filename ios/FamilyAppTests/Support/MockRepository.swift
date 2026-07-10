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
    struct LoginRecord: Equatable { let email: String
        let password: String
    }

    struct RegisterRecord: Equatable { let name: String
        let email: String
        let password: String
        let birthday: String
        let mobile: String
    }

    private(set) var loginCalls: [LoginRecord] = []
    private(set) var registerCalls: [RegisterRecord] = []

    // App lifecycle (RootViewModel)
    private(set) var touchLastActiveCalled = false
    private(set) var syncPushTokenCalled = false
    private(set) var syncNotificationPrefsCalled = false

    // Family photo
    var familyPhotoURLResult = "https://example.com/photo.jpg"
    private(set) var uploadedFamilyPhotos: [String] = []

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

    func uploadFamilyPhotoImage(familyId: String, data _: Data) async throws -> String {
        uploadedFamilyPhotos.append(familyId)
        return familyPhotoURLResult
    }

    /// App lifecycle (RootViewModel)
    func touchLastActive() async {
        touchLastActiveCalled = true
    }

    func syncPushToken() async {
        syncPushTokenCalled = true
    }

    func syncNotificationPrefsToServer() async {
        syncNotificationPrefsCalled = true
    }

    /// Profile
    func updateProfile(userId: String, update: ProfileUpdate) async {
        updatedProfiles.append((userId, update))
    }

    /// Auth
    func login(email: String, password: String) async throws -> String {
        loginCalls.append(LoginRecord(email: email, password: password))
        return loginResult
    }

    func register(
        name: String, email: String, password: String, birthday: String, mobile: String
    ) async throws {
        registeredUsers.append(name)
        registerCalls.append(
            RegisterRecord(name: name, email: email, password: password, birthday: birthday, mobile: mobile)
        )
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

    func authSignedInEvents() -> AsyncStream<Void> {
        AsyncStream { $0.finish() }
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

    // Chat — reads/writes used by ChatViewModel.
    // Canned reads keyed so unread/preview logic can be driven declaratively.
    var conversationsResult: [ConversationModel] = []
    var messagesByConversation: [String: [MessageModel]] = [:]
    var participantsByConversation: [String: [ConversationParticipantModel]] = [:]
    var reactionsByConversation: [String: [MessageReactionModel]] = [:]
    var insertConversationResult = ConversationModel()
    var groupImageURLResult = "https://example.com/group.jpg"

    struct ConversationInsert: Equatable { let userFrom: String
        let name: String
        let familyId: String?
    }

    struct TextMessageInsert: Equatable { let conversationId: String
        let userFrom: String
        let text: String
        let replyToId: String?
    }

    struct SystemMessageInsert: Equatable { let conversationId: String
        let userFrom: String
        let text: String
    }

    struct MediaMessageInsert: Equatable { let conversationId: String
        let userFrom: String
        let mediaUrl: String
    }

    private(set) var insertedConversations: [ConversationInsert] = []
    private(set) var insertedParticipants: [(conversationId: String, userId: String)] = []
    private(set) var deletedParticipants: [(conversationId: String, userId: String)] = []
    private(set) var renamedConversations: [(id: String, name: String)] = []
    private(set) var setConversationImages: [(id: String, url: String)] = []
    private(set) var clearedConversationImageIds: [String] = []
    private(set) var deletedConversationIds: [String] = []
    private(set) var insertedTextMessages: [TextMessageInsert] = []
    private(set) var insertedSystemMessages: [SystemMessageInsert] = []
    private(set) var insertedImageMessages: [MediaMessageInsert] = []
    private(set) var insertedVoiceMessages: [MediaMessageInsert] = []
    private(set) var uploadedGroupImageIds: [String] = []
    private(set) var removedGroupImageIds: [String] = []

    func fetchConversations() async throws -> [ConversationModel] {
        conversationsResult
    }

    func fetchConversation(id: String) async throws -> [ConversationModel] {
        conversationsResult.filter { $0.id == id }
    }

    func fetchMessages(conversationId: String) async throws -> [MessageModel] {
        (messagesByConversation[conversationId] ?? []).sorted { $0.sentAt < $1.sentAt }
    }

    func fetchMyParticipants(
        userId: String, conversationIds: [String]
    ) async throws -> [ConversationParticipantModel] {
        conversationIds
            .flatMap { participantsByConversation[$0] ?? [] }
            .filter { $0.userId == userId }
    }

    /// Counts messages from someone other than me newer than `after`, driving the
    /// unread-count tests from canned messages.
    func countUnreadMessages(conversationId: String, userId: String, after: String) async -> Int {
        (messagesByConversation[conversationId] ?? [])
            .filter { $0.userFrom != userId && $0.sentAt > after }
            .count
    }

    func fetchParticipants(conversationIds: [String]) async throws -> [ConversationParticipantModel] {
        conversationIds.flatMap { participantsByConversation[$0] ?? [] }
    }

    func fetchParticipants(conversationId: String) async throws -> [ConversationParticipantModel] {
        participantsByConversation[conversationId] ?? []
    }

    func fetchParticipants(userId: String) async throws -> [ConversationParticipantModel] {
        participantsByConversation.values.flatMap { $0 }.filter { $0.userId == userId }
    }

    func fetchUsers(ids: [String]) async throws -> [UserModel] {
        ids.compactMap { users[$0] }
    }

    func fetchReactions(conversationId: String) async throws -> [MessageReactionModel] {
        reactionsByConversation[conversationId] ?? []
    }

    func insertConversation(
        userFrom: String, name: String, familyId: String?
    ) async throws -> ConversationModel {
        insertedConversations.append(
            ConversationInsert(userFrom: userFrom, name: name, familyId: familyId)
        )
        return insertConversationResult
    }

    func insertParticipant(conversationId: String, userId: String) async throws {
        insertedParticipants.append((conversationId, userId))
    }

    func deleteParticipant(conversationId: String, userId: String) async {
        deletedParticipants.append((conversationId, userId))
    }

    func renameConversation(id: String, name: String) async {
        renamedConversations.append((id, name))
    }

    func setConversationImage(id: String, url: String) async throws {
        setConversationImages.append((id, url))
    }

    func clearConversationImage(id: String) async {
        clearedConversationImageIds.append(id)
    }

    func deleteConversation(id: String) async throws {
        deletedConversationIds.append(id)
    }

    func insertTextMessage(
        conversationId: String, userFrom: String, text: String, replyToId: String?
    ) async throws {
        insertedTextMessages.append(
            TextMessageInsert(
                conversationId: conversationId, userFrom: userFrom, text: text, replyToId: replyToId
            )
        )
    }

    func insertSystemMessage(conversationId: String, userFrom: String, text: String) async {
        insertedSystemMessages.append(
            SystemMessageInsert(conversationId: conversationId, userFrom: userFrom, text: text)
        )
    }

    func insertImageMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws {
        insertedImageMessages.append(
            MediaMessageInsert(conversationId: conversationId, userFrom: userFrom, mediaUrl: mediaUrl)
        )
    }

    func insertVoiceMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws {
        insertedVoiceMessages.append(
            MediaMessageInsert(conversationId: conversationId, userFrom: userFrom, mediaUrl: mediaUrl)
        )
    }

    func uploadGroupImage(conversationId: String, data _: Data) async throws -> String {
        uploadedGroupImageIds.append(conversationId)
        return groupImageURLResult
    }

    func removeGroupImage(conversationId: String) async {
        removedGroupImageIds.append(conversationId)
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

    // Meal
    var mealPlanDetailResult: [MealPlanModel] = []
    var mealPlanDaysForPlanResult: [MealPlanDayModel] = []
    var mealPlanDaysForIdsResult: [MealPlanDayModel] = []
    var insertMealPlanResult = MealPlanModel()
    struct MealDayRecord: Equatable { let mealPlanId: String
        let day: String
        let date: String
    }

    private(set) var insertedMealPlans: [MealPlanModel] = []
    private(set) var insertedMealPlanDays: [MealDayRecord] = []
    private(set) var renamedMealPlans: [(id: String, name: String)] = []
    private(set) var mealPlanIcons: [(id: String, icon: String)] = []
    private(set) var mealPlanColors: [(id: String, color: Int?)] = []
    private(set) var deletedMealPlanIds: [String] = []
    private(set) var mealDayFoods: [(id: String, food: String)] = []

    func fetchMealPlanDays(mealPlanIds: [String]) async throws -> [MealPlanDayModel] {
        guard !mealPlanIds.isEmpty else { return [] }
        return mealPlanDaysForIdsResult
    }

    func fetchMealPlans(planId _: String) async throws -> [MealPlanModel] {
        mealPlanDetailResult
    }

    func fetchMealPlanDays(mealPlanId _: String) async throws -> [MealPlanDayModel] {
        mealPlanDaysForPlanResult
    }

    func insertMealPlan(_ plan: MealPlanModel) async throws -> MealPlanModel {
        insertedMealPlans.append(plan)
        return insertMealPlanResult
    }

    func insertMealPlanDay(mealPlanId: String, day: String, date: String) async throws {
        insertedMealPlanDays.append(MealDayRecord(mealPlanId: mealPlanId, day: day, date: date))
    }

    func renameMealPlan(id: String, name: String) async {
        renamedMealPlans.append((id, name))
    }

    func setMealPlanIcon(id: String, icon: String) async {
        mealPlanIcons.append((id, icon))
    }

    func setMealPlanColor(id: String, color: Int?) async {
        mealPlanColors.append((id, color))
    }

    func deleteMealPlan(id: String) async {
        deletedMealPlanIds.append(id)
    }

    func setMealDayFood(id: String, food: String) async {
        mealDayFoods.append((id, food))
    }

    // Shopping
    var shoppingListsForUserResult: [ShoppingListModel] = []
    var shoppingItemsForIdsResult: [ShoppingItemModel] = []
    var shoppingListDetailResult: [ShoppingListModel] = []
    var shoppingItemsForListResult: [ShoppingItemModel] = []
    private(set) var insertedShoppingLists: [ShoppingListModel] = []
    private(set) var shoppingListColors: [(id: String, color: Int?)] = []
    private(set) var shoppingListIcons: [(id: String, icon: String)] = []
    private(set) var renamedShoppingLists: [(id: String, title: String)] = []
    private(set) var deletedShoppingListIds: [String] = []
    private(set) var insertedShoppingItems: [ShoppingItemModel] = []
    private(set) var shoppingItemChecks: [(id: String, checked: Bool)] = []
    private(set) var renamedShoppingItems: [(id: String, item: String)] = []
    private(set) var deletedShoppingItemIds: [String] = []
    private(set) var clearedCompletedListIds: [String] = []

    func fetchShoppingLists(userId _: String, familyId _: String?) async throws -> [ShoppingListModel] {
        shoppingListsForUserResult
    }

    func fetchShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel] {
        guard !listIds.isEmpty else { return [] }
        return shoppingItemsForIdsResult
    }

    func fetchShoppingList(id _: String) async throws -> [ShoppingListModel] {
        shoppingListDetailResult
    }

    func fetchShoppingItems(listId _: String) async throws -> [ShoppingItemModel] {
        shoppingItemsForListResult
    }

    func insertShoppingList(_ list: ShoppingListModel) async {
        insertedShoppingLists.append(list)
    }

    func setShoppingListColor(id: String, color: Int?) async {
        shoppingListColors.append((id, color))
    }

    func setShoppingListIcon(id: String, icon: String) async {
        shoppingListIcons.append((id, icon))
    }

    func renameShoppingList(id: String, title: String) async {
        renamedShoppingLists.append((id, title))
    }

    func deleteShoppingList(id: String) async {
        deletedShoppingListIds.append(id)
    }

    func insertShoppingItem(_ item: ShoppingItemModel) async {
        insertedShoppingItems.append(item)
    }

    func setShoppingItemChecked(id: String, checked: Bool) async {
        shoppingItemChecks.append((id, checked))
    }

    func renameShoppingItem(id: String, item: String) async {
        renamedShoppingItems.append((id, item))
    }

    func deleteShoppingItem(id: String) async {
        deletedShoppingItemIds.append(id)
    }

    func clearCompletedShoppingItems(listId: String) async {
        clearedCompletedListIds.append(listId)
    }

    // Wishlist
    var wishlistsForUserResult: [WishlistModel] = []
    var wishlistDetailResult: [WishlistModel] = []
    var wishesForListResult: [WishModel] = []
    var wishReservationsResult: [WishReservationModel] = []
    var usersByIdResult: [UserModel] = []
    struct ReservationRecord: Equatable { let wishId: String
        let reservedBy: String
    }

    private(set) var insertedWishlists: [WishlistModel] = []
    private(set) var wishlistColors: [(id: String, color: Int?)] = []
    private(set) var wishlistIcons: [(id: String, icon: String)] = []
    private(set) var renamedWishlists: [(id: String, name: String)] = []
    private(set) var deletedWishlistIds: [String] = []
    private(set) var insertedWishes: [WishModel] = []
    private(set) var wishChecks: [(id: String, checked: Bool)] = []
    private(set) var deletedWishIds: [String] = []
    private(set) var insertedReservations: [ReservationRecord] = []
    private(set) var deletedReservations: [ReservationRecord] = []

    func fetchWishlists(userId _: String, familyId _: String?) async throws -> [WishlistModel] {
        wishlistsForUserResult
    }

    func fetchWishlist(id _: String) async throws -> [WishlistModel] {
        wishlistDetailResult
    }

    func fetchWishes(wishlistId _: String) async throws -> [WishModel] {
        wishesForListResult
    }

    func fetchWishReservations(wishIds: [String]) async throws -> [WishReservationModel] {
        guard !wishIds.isEmpty else { return [] }
        return wishReservationsResult
    }

    func fetchUser(id _: String) async throws -> [UserModel] {
        usersByIdResult
    }

    func insertWishlist(_ list: WishlistModel) async {
        insertedWishlists.append(list)
    }

    func setWishlistColor(id: String, color: Int?) async {
        wishlistColors.append((id, color))
    }

    func setWishlistIcon(id: String, icon: String) async {
        wishlistIcons.append((id, icon))
    }

    func renameWishlist(id: String, name: String) async {
        renamedWishlists.append((id, name))
    }

    func deleteWishlist(id: String) async {
        deletedWishlistIds.append(id)
    }

    func insertWish(_ wish: WishModel) async {
        insertedWishes.append(wish)
    }

    func setWishChecked(id: String, checked: Bool) async {
        wishChecks.append((id, checked))
    }

    struct WishUpdateRecord: Equatable {
        let id: String
        let text: String
        let link: String?
        let price: String?
        let imageUrl: String?
    }

    private(set) var updatedWishes: [WishUpdateRecord] = []
    func updateWish(id: String, text: String, link: String?, price: String?, imageUrl: String?) async {
        updatedWishes.append(WishUpdateRecord(id: id, text: text, link: link, price: price, imageUrl: imageUrl))
    }

    func deleteWish(id: String) async {
        deletedWishIds.append(id)
    }

    func insertWishReservation(wishId: String, reservedBy: String) async {
        insertedReservations.append(ReservationRecord(wishId: wishId, reservedBy: reservedBy))
    }

    func deleteWishReservation(wishId: String, reservedBy: String) async {
        deletedReservations.append(ReservationRecord(wishId: wishId, reservedBy: reservedBy))
    }

    // Wishlist share links
    var sharedWishlistsResult: [WishlistModel] = []
    var ensureShareTokenResult: String?
    var acceptShareResult: String?
    private(set) var ensuredShareTokenWishlistIds: [String] = []
    private(set) var acceptedShareTokens: [String] = []

    func fetchSharedWishlists(userId _: String) async throws -> [WishlistModel] {
        sharedWishlistsResult
    }

    func ensureWishlistShareToken(wishlistId: String) async throws -> String? {
        ensuredShareTokenWishlistIds.append(wishlistId)
        return ensureShareTokenResult
    }

    func acceptWishlistShare(token: String) async throws -> String? {
        acceptedShareTokens.append(token)
        return acceptShareResult
    }

    // Map
    var userLocationsResult: [UserLocationModel] = []
    private(set) var upsertedLocations: [UserLocationModel] = []
    private(set) var clearedLocationUserIds: [String] = []

    func fetchUserLocations(familyId _: String) async throws -> [UserLocationModel] {
        userLocationsResult
    }

    func upsertUserLocation(_ location: UserLocationModel) async {
        upsertedLocations.append(location)
    }

    func clearUserLocationVisibility(userId: String) async {
        clearedLocationUserIds.append(userId)
    }
}
