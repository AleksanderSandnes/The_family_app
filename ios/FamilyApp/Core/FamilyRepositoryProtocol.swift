// The dependency seam for ViewModels. Every VM depends on this protocol (injected via
// init) rather than the concrete FamilyRepository singleton, so tests can substitute a
// MockRepository with no live Supabase backend. FamilyRepository conforms as-is.
import Foundation

@MainActor
protocol FamilyRepositoryProtocol: AnyObject {
    // Session / prefs
    var session: SessionStore { get }
    var pendingJoinCode: String? { get }

    // Users / family reads
    func getUser(_ userId: String) async -> UserModel?
    func getFamily(familyId: String) async -> FamilyModel?
    func isFamilyAdmin(userId: String) async -> Bool
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
    func uploadFamilyPhotoImage(familyId: String, data: Data) async throws -> String

    // App lifecycle (used by RootViewModel)
    func touchLastActive() async
    func syncPushToken() async
    func syncNotificationPrefsToServer() async

    /// Profile
    func updateProfile(userId: String, update: ProfileUpdate) async

    // Auth
    func login(email: String, password: String) async throws -> String
    func register(name: String, email: String, password: String, birthday: String, mobile: String) async throws
    func signInWithGoogle() async throws
    func sendPasswordResetEmail(email: String) async throws
    func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String
    func hasAuthSession() -> Bool
    func confirmSignupEmail(email: String, code: String) async throws -> String
    func resendSignupCode(email: String) async throws
    func signOut() async
    func completeSignInAfterConfirmation() async throws -> String
    func authSignedInEvents() -> AsyncStream<Void>
    func consumePendingJoinCode() -> String?
    func setPendingJoinCode(_ code: String)

    // Chat
    func getLastMessage(conversationId: String) async -> MessageModel?
    func markConversationRead(conversationId: String) async
    func sendMessage(conversationId: String, text: String) async throws
    func editMessage(messageId: String, newText: String) async throws
    func deleteMessage(messageId: String) async throws
    func addReaction(messageId: String, conversationId: String, emoji: String) async throws
    func removeReaction(messageId: String) async throws
    // Chat — reads/writes
    func fetchConversations() async throws -> [ConversationModel]
    func fetchConversation(id: String) async throws -> [ConversationModel]
    func fetchMessages(conversationId: String) async throws -> [MessageModel]
    func fetchMyParticipants(userId: String, conversationIds: [String]) async throws
        -> [ConversationParticipantModel]
    func countUnreadMessages(conversationId: String, userId: String, after: String) async -> Int
    func fetchParticipants(conversationIds: [String]) async throws -> [ConversationParticipantModel]
    func fetchParticipants(conversationId: String) async throws -> [ConversationParticipantModel]
    func fetchParticipants(userId: String) async throws -> [ConversationParticipantModel]
    func fetchUsers(ids: [String]) async throws -> [UserModel]
    func fetchReactions(conversationId: String) async throws -> [MessageReactionModel]
    func insertConversation(userFrom: String, name: String, familyId: String?) async throws
        -> ConversationModel
    func insertParticipant(conversationId: String, userId: String) async throws
    func deleteParticipant(conversationId: String, userId: String) async
    func renameConversation(id: String, name: String) async
    func setConversationImage(id: String, url: String) async throws
    func clearConversationImage(id: String) async
    func deleteConversation(id: String) async throws
    func insertTextMessage(
        conversationId: String, userFrom: String, text: String, replyToId: String?
    ) async throws
    func insertSystemMessage(conversationId: String, userFrom: String, text: String) async
    func insertImageMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws
    func insertVoiceMessage(conversationId: String, userFrom: String, mediaUrl: String) async throws
    func uploadGroupImage(conversationId: String, data: Data) async throws -> String
    func removeGroupImage(conversationId: String) async

    // Settings
    func setThemeMode(_ mode: ThemeMode)
    func setNotificationsEnabled(_ enabled: Bool) async
    func setNotifyDaysBefore(_ days: Int) async
    func setLocationVisible(_ visible: Bool)

    // Birthdays
    func fetchBirthdays(userId: String, familyId: String?) async throws -> [BirthdayModel]
    func insertBirthday(_ birthday: BirthdayModel) async
    func updateBirthday(id: String, name: String, date: String, icon: String, color: Int?) async
    func deleteBirthday(id: String) async

    // Calendar
    func fetchCalendarEvents(userId: String, familyId: String?) async throws -> [CalendarEventModel]
    func insertCalendarEvent(_ event: CalendarEventModel) async
    func updateCalendarEvent(_ event: CalendarEventModel) async
    func deleteCalendarEvent(id: String) async

    // Home
    func fetchMealPlans(familyId: String) async throws -> [MealPlanModel]
    func fetchMealPlanDays(mealPlanId: String, date: String) async throws -> [MealPlanDayModel]
    func fetchFamilyCalendarEvents(familyId: String) async throws -> [CalendarEventModel]
    func fetchFamilyBirthdays(familyId: String) async throws -> [BirthdayModel]
    func fetchShoppingLists(familyId: String) async throws -> [ShoppingListModel]
    func fetchUncheckedShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel]

    // Meal (fetchMealPlans(familyId:) above is reused)
    func fetchMealPlanDays(mealPlanIds: [String]) async throws -> [MealPlanDayModel]
    func fetchMealPlans(planId: String) async throws -> [MealPlanModel]
    func fetchMealPlanDays(mealPlanId: String) async throws -> [MealPlanDayModel]
    func insertMealPlan(_ plan: MealPlanModel) async throws -> MealPlanModel
    func insertMealPlanDay(mealPlanId: String, day: String, date: String) async throws
    func renameMealPlan(id: String, name: String) async
    func setMealPlanIcon(id: String, icon: String) async
    func setMealPlanColor(id: String, color: Int?) async
    func deleteMealPlan(id: String) async
    func setMealDayFood(id: String, food: String) async

    // Shopping (fetchShoppingLists(familyId:) & fetchUncheckedShoppingItems(listIds:) above
    // are family/unchecked-scoped and not reused)
    func fetchShoppingLists(userId: String, familyId: String?) async throws -> [ShoppingListModel]
    func fetchShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel]
    func fetchShoppingList(id: String) async throws -> [ShoppingListModel]
    func fetchShoppingItems(listId: String) async throws -> [ShoppingItemModel]
    func insertShoppingList(_ list: ShoppingListModel) async
    func setShoppingListColor(id: String, color: Int?) async
    func setShoppingListIcon(id: String, icon: String) async
    func renameShoppingList(id: String, title: String) async
    func deleteShoppingList(id: String) async
    func insertShoppingItem(_ item: ShoppingItemModel) async
    func setShoppingItemChecked(id: String, checked: Bool) async
    func renameShoppingItem(id: String, item: String) async
    func deleteShoppingItem(id: String) async
    func clearCompletedShoppingItems(listId: String) async

    // Wishlist (reservations are insert/delete only)
    func fetchWishlists(userId: String, familyId: String?) async throws -> [WishlistModel]
    func fetchWishlist(id: String) async throws -> [WishlistModel]
    func fetchWishes(wishlistId: String) async throws -> [WishModel]
    func fetchWishReservations(wishIds: [String]) async throws -> [WishReservationModel]
    func fetchUser(id: String) async throws -> [UserModel]
    func insertWishlist(_ list: WishlistModel) async
    func setWishlistColor(id: String, color: Int?) async
    func setWishlistIcon(id: String, icon: String) async
    func renameWishlist(id: String, name: String) async
    func deleteWishlist(id: String) async
    func insertWish(_ wish: WishModel) async
    func setWishChecked(id: String, checked: Bool) async
    func updateWish(id: String, text: String, link: String?, price: String?, imageUrl: String?) async
    func deleteWish(id: String) async
    func insertWishReservation(wishId: String, reservedBy: String) async
    func deleteWishReservation(wishId: String, reservedBy: String) async
    func fetchSharedWishlists(userId: String) async throws -> [WishlistModel]
    func ensureWishlistShareToken(wishlistId: String) async throws -> String?
    func acceptWishlistShare(token: String) async throws -> String?

    // Map
    func fetchUserLocations(familyId: String) async throws -> [UserLocationModel]
    func upsertUserLocation(_ location: UserLocationModel) async
    func clearUserLocationVisibility(userId: String) async
}

/// FamilyRepository implements every method above; this records the conformance.
extension FamilyRepository: FamilyRepositoryProtocol {}
