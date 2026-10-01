@testable import FamilyApp

// FamilyRepository list features against the stubbed Supabase transport: shopping,
// wishlists, meals, calendar, birthdays, home aggregates, map and settings mirrors.
import XCTest

@MainActor
final class RepositoryListsBackendTests: XCTestCase {
    private let repo = FamilyRepository.shared

    override func setUp() async throws {
        try await super.setUp()
        StubSupabase.install()
    }

    override func tearDown() async throws {
        StubSupabase.uninstall()
        try await super.tearDown()
    }

    // MARK: - Shopping

    func testShoppingListsAreScopedToOwnerOrFamily() async throws {
        StubTransport.respond(
            table: "shopping_lists",
            with: [
                ShoppingListModel(id: "mine", title: "Groceries", ownerUserId: "emma", familyId: "fam-1"),
                ShoppingListModel(id: "private", title: "Hardware", ownerUserId: "emma"),
                ShoppingListModel(id: "foreign", title: "Other family", ownerUserId: "emma", familyId: "fam-9"),
            ]
        )
        let withFamily = try await repo.fetchShoppingLists(userId: "emma", familyId: "fam-1")
        let solo = try await repo.fetchShoppingLists(userId: "emma", familyId: nil)
        XCTAssertEqual(withFamily.map(\.id), ["mine", "private"])
        XCTAssertEqual(solo.map(\.id), ["private"])
        let reads = StubTransport.requests(to: "/rest/v1/shopping_lists")
        XCTAssertTrue(reads[0].hasFilter("or", "(owner_user_id.eq.emma,family_id.eq.fam-1)"))
        XCTAssertTrue(reads[1].hasFilter("owner_user_id", "eq.emma"))
    }

    func testShoppingReadsAndWrites() async throws {
        StubTransport.respond(table: "shopping_items", with: [ShoppingItemModel(
            id: "i1",
            listId: "l1",
            item: "Oat milk"
        )])
        StubTransport.respond(table: "shopping_lists", with: [ShoppingListModel(id: "l1", title: "Groceries")])

        let many = try await repo.fetchShoppingItems(listIds: ["l1"])
        let none = try await repo.fetchShoppingItems(listIds: [])
        let list = try await repo.fetchShoppingList(id: "l1")
        let items = try await repo.fetchShoppingItems(listId: "l1")
        await repo.insertShoppingList(ShoppingListModel(title: "Groceries", ownerUserId: "emma", familyId: "fam-1"))
        await repo.insertShoppingList(ShoppingListModel(title: "Private", ownerUserId: "emma", color: 0xFF14B8A6))
        await repo.setShoppingListColor(id: "l1", color: nil)
        await repo.setShoppingListIcon(id: "l1", icon: "kitchen")
        await repo.renameShoppingList(id: "l1", title: "Weekly shop")
        await repo.insertShoppingItem(ShoppingItemModel(listId: "l1", item: "Tomatoes"))
        await repo.setShoppingItemChecked(id: "i1", checked: true)
        await repo.renameShoppingItem(id: "i1", item: "Cherry tomatoes")
        await repo.deleteShoppingItem(id: "i1")
        await repo.clearCompletedShoppingItems(listId: "l1")
        await repo.deleteShoppingList(id: "l1")

        XCTAssertEqual(many.first?.item, "Oat milk")
        XCTAssertTrue(none.isEmpty)
        XCTAssertEqual(list.first?.title, "Groceries")
        XCTAssertEqual(items.count, 1)
        let listInserts = StubTransport.requests(to: "/rest/v1/shopping_lists", method: "POST").map(\.jsonObject)
        XCTAssertEqual(listInserts[0]["family_id"] as? String, "fam-1")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/shopping_lists", method: "PATCH").count, 3)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/shopping_items", method: "POST").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/shopping_items", method: "PATCH").count, 2)
        let deletes = StubTransport.requests(to: "/rest/v1/shopping_items", method: "DELETE")
        XCTAssertEqual(deletes.count, 2)
        XCTAssertTrue(deletes[1].hasFilter("checked", "eq.true"))
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/shopping_lists", method: "DELETE").count, 1)
    }

    // MARK: - Wishlists

    func testWishlistScopingSharingAndReads() async throws {
        StubTransport.respond(
            table: "wishlists",
            with: [
                WishlistModel(id: "w1", ownerUserId: "nora", familyId: "fam-1", name: "Birthday"),
                WishlistModel(id: "w2", ownerUserId: "nora", name: "Private"),
                WishlistModel(id: "w3", ownerUserId: "nora", familyId: "fam-9", name: "Foreign"),
            ]
        )
        StubTransport.respond("GET", "/rest/v1/wishlist_shares", body: StubJSON.literal([["wishlist_id": "w1"]]))
        StubTransport.respond(table: "wishes", with: [WishModel(
            id: "wish-1",
            wishlistId: "w1",
            text: "Football boots"
        )])
        StubTransport.respond(table: "wish_reservations", with: [WishReservationModel(id: "res", wishId: "wish-1")])
        StubTransport.respond(table: "users", with: [UserModel(id: "nora", name: "Nora")])
        StubTransport.respond("POST", "/rest/v1/rpc/ensure_wishlist_share_token", body: StubJSON.literal("tok-1"))
        StubTransport.respond("POST", "/rest/v1/rpc/accept_wishlist_share", body: StubJSON.literal("w1"))

        let family = try await repo.fetchWishlists(userId: "nora", familyId: "fam-1")
        let solo = try await repo.fetchWishlists(userId: "nora", familyId: nil)
        let one = try await repo.fetchWishlist(id: "w1")
        let wishes = try await repo.fetchWishes(wishlistId: "w1")
        let reservations = try await repo.fetchWishReservations(wishIds: ["wish-1"])
        let owner = try await repo.fetchUser(id: "nora")
        let shared = try await repo.fetchSharedWishlists(userId: "emma")
        let token = try await repo.ensureWishlistShareToken(wishlistId: "w1")
        let accepted = try await repo.acceptWishlistShare(token: "tok-1")

        XCTAssertEqual(family.map(\.id), ["w1", "w2"])
        XCTAssertEqual(solo.map(\.id), ["w2"])
        XCTAssertEqual(one.count, 3)
        XCTAssertEqual(wishes.first?.text, "Football boots")
        XCTAssertEqual(reservations.first?.wishId, "wish-1")
        XCTAssertEqual(owner.first?.name, "Nora")
        XCTAssertEqual(shared.count, 3)
        XCTAssertEqual(token, "tok-1")
        XCTAssertEqual(accepted, "w1")
    }

    func testSharedWishlistsWithoutGrantsSkipTheSecondQuery() async throws {
        let shared = try await repo.fetchSharedWishlists(userId: "emma")
        XCTAssertTrue(shared.isEmpty)
        XCTAssertTrue(StubTransport.requests(to: "/rest/v1/wishlists").isEmpty)
    }

    func testWishlistAndWishWrites() async throws {
        await repo.insertWishlist(WishlistModel(ownerUserId: "nora", familyId: "fam-1", name: "Birthday"))
        await repo.insertWishlist(WishlistModel(ownerUserId: "nora", name: "Private", color: 0xFFEC4899))
        await repo.setWishlistColor(id: "w1", color: 0xFFEC4899)
        await repo.setWishlistIcon(id: "w1", icon: "cake")
        await repo.renameWishlist(id: "w1", name: "Nora's birthday")
        await repo.insertWish(WishModel(
            wishlistId: "w1",
            userId: "nora",
            text: "Boots",
            link: "https://example.com",
            price: "699 kr",
            imageUrl: "wish-images/a.jpg"
        ))
        await repo.insertWish(WishModel(wishlistId: "w1", userId: "nora", text: "Plain", link: "", price: ""))
        await repo.setWishChecked(id: "wish-1", checked: true)
        await repo.updateWish(
            id: "wish-1",
            update: WishUpdate(text: "Boots", link: nil, price: "650 kr", imageUrl: nil, description: "Size 36")
        )
        await repo.insertWishReservation(wishId: "wish-1", reservedBy: "emma")
        await repo.deleteWishReservation(wishId: "wish-1", reservedBy: "emma")
        await repo.deleteWish(id: "wish-1")
        await repo.deleteWishlist(id: "w1")

        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wishlists", method: "POST").count, 2)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wishlists", method: "PATCH").count, 3)
        let wishInserts = StubTransport.requests(to: "/rest/v1/wishes", method: "POST").map(\.jsonObject)
        XCTAssertEqual(wishInserts[0]["price"] as? String, "699 kr")
        XCTAssertEqual(wishInserts[0]["image_url"] as? String, "wish-images/a.jpg")
        XCTAssertNil(wishInserts[1]["link"])
        XCTAssertNil(wishInserts[1]["price"])
        let wishUpdates = StubTransport.requests(to: "/rest/v1/wishes", method: "PATCH").map(\.jsonObject)
        XCTAssertEqual(wishUpdates[1]["description"] as? String, "Size 36")
        XCTAssertTrue(wishUpdates[1]["link"] is NSNull)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wish_reservations", method: "POST").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wish_reservations", method: "DELETE").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wishes", method: "DELETE").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/wishlists", method: "DELETE").count, 1)
    }

    // MARK: - Meals

    func testMealPlanReadsAndWrites() async throws {
        let plan = MealPlanModel(
            id: "plan-1",
            familyId: "fam-1",
            fromDate: "2026-09-28",
            toDate: "2026-10-04",
            week: 40,
            name: "This week"
        )
        StubTransport.respond(table: "meal_plans", with: [plan])
        StubTransport.respond("POST", "/rest/v1/meal_plans", body: StubJSON.encode(plan))
        StubTransport.respond(
            table: "meal_plan_days",
            with: [MealPlanDayModel(id: "d1", mealPlanId: "plan-1", day: "Friday", date: "2026-10-02", food: "Tacos")]
        )

        let byFamily = try await repo.fetchMealPlans(familyId: "fam-1")
        let byId = try await repo.fetchMealPlans(planId: "plan-1")
        let daysForDate = try await repo.fetchMealPlanDays(mealPlanId: "plan-1", date: "2026-10-02")
        let daysForPlans = try await repo.fetchMealPlanDays(mealPlanIds: ["plan-1"])
        let noDays = try await repo.fetchMealPlanDays(mealPlanIds: [])
        let allDays = try await repo.fetchMealPlanDays(mealPlanId: "plan-1")
        let inserted = try await repo.insertMealPlan(plan)
        try await repo.insertMealPlanDay(mealPlanId: "plan-1", day: "Monday", date: "2026-09-28")
        await repo.renameMealPlan(id: "plan-1", name: "Next week")
        await repo.setMealPlanIcon(id: "plan-1", icon: "local_pizza")
        await repo.setMealPlanColor(id: "plan-1", color: nil)
        await repo.setMealDayFood(id: "d1", food: "Homemade pizza")
        await repo.deleteMealPlan(id: "plan-1")

        XCTAssertEqual(byFamily.first?.name, "This week")
        XCTAssertEqual(byId.count, 1)
        XCTAssertEqual(daysForDate.first?.food, "Tacos")
        XCTAssertEqual(daysForPlans.count, 1)
        XCTAssertTrue(noDays.isEmpty)
        XCTAssertEqual(allDays.count, 1)
        XCTAssertEqual(inserted.id, "plan-1")
        let insert = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/meal_plans", method: "POST").first)
        XCTAssertEqual(insert.jsonObject["week"] as? Int, 40)
        XCTAssertTrue(insert.jsonObject["color"] is NSNull)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/meal_plans", method: "PATCH").count, 3)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/meal_plan_days", method: "PATCH").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/meal_plans", method: "DELETE").count, 1)
    }

    // MARK: - Calendar and birthdays

    func testCalendarScopingAndWrites() async throws {
        StubTransport.respond(
            table: "calendar_events",
            with: [
                CalendarEventModel(id: "e1", userId: "emma", familyId: "fam-1", activity: "Football practice"),
                CalendarEventModel(id: "e2", userId: "emma", activity: "Dentist"),
                CalendarEventModel(id: "e3", userId: "emma", familyId: "fam-9", activity: "Foreign"),
            ]
        )
        let family = try await repo.fetchCalendarEvents(userId: "emma", familyId: "fam-1")
        let solo = try await repo.fetchCalendarEvents(userId: "emma", familyId: nil)
        let familyOnly = try await repo.fetchFamilyCalendarEvents(familyId: "fam-1")
        var event = CalendarEventModel(
            id: "e1",
            userId: "emma",
            familyId: "fam-1",
            dateFrom: "2026-10-02",
            dateTo: "2026-10-02",
            activity: "Swim"
        )
        await repo.insertCalendarEvent(event)
        event.familyId = nil
        event.color = 0xFF6366F1
        event.attendeeIds = ["emma", "jonas"]
        await repo.insertCalendarEvent(event)
        await repo.updateCalendarEvent(event)
        await repo.deleteCalendarEvent(id: "e1")

        XCTAssertEqual(family.map(\.id), ["e1", "e2"])
        XCTAssertEqual(solo.map(\.id), ["e2"])
        XCTAssertEqual(familyOnly.count, 3)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/calendar_events", method: "POST").count, 2)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/calendar_events", method: "PATCH").count, 1)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/calendar_events", method: "DELETE").count, 1)
    }

    func testBirthdayScopingAndWrites() async throws {
        StubTransport.respond(
            table: "birthdays",
            with: [
                BirthdayModel(id: "b1", name: "Nora", date: "2014-06-21", familyId: "fam-1", madeByUserId: "emma"),
                BirthdayModel(id: "b2", name: "Grandma Ingrid", date: "1950-10-13", madeByUserId: "emma"),
                BirthdayModel(id: "b3", name: "Foreign", date: "2000-01-01", familyId: "fam-9", madeByUserId: "emma"),
            ]
        )
        let family = try await repo.fetchBirthdays(userId: "emma", familyId: "fam-1")
        let solo = try await repo.fetchBirthdays(userId: "emma", familyId: nil)
        let familyOnly = try await repo.fetchFamilyBirthdays(familyId: "fam-1")
        await repo.insertBirthday(BirthdayModel(
            name: "Jonas",
            date: "2017-11-30",
            familyId: "fam-1",
            madeByUserId: "emma"
        ))
        await repo.insertBirthday(BirthdayModel(
            name: "Friend",
            date: "1990-01-01",
            madeByUserId: "emma",
            color: 0xFFF59E0B
        ))
        await repo.updateBirthday(id: "b1", name: "Nora N.", date: "2014-06-21", icon: "celebration", color: nil)
        await repo.deleteBirthday(id: "b1")

        XCTAssertEqual(family.map(\.id), ["b1", "b2"])
        XCTAssertEqual(solo.count, 3)
        XCTAssertEqual(familyOnly.count, 3)
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/birthdays", method: "POST").count, 2)
        let update = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/birthdays", method: "PATCH").first)
        XCTAssertEqual(update.jsonObject["icon"] as? String, "celebration")
        XCTAssertEqual(StubTransport.requests(to: "/rest/v1/birthdays", method: "DELETE").count, 1)
    }

    // MARK: - Home aggregates

    func testHomeAggregateReads() async throws {
        StubTransport.respond(table: "shopping_lists", with: [ShoppingListModel(
            id: "l1",
            title: "Groceries",
            familyId: "fam-1"
        )])
        StubTransport.respond(table: "shopping_items", with: [ShoppingItemModel(
            id: "i1",
            listId: "l1",
            item: "Apples"
        )])
        let lists = try await repo.fetchShoppingLists(familyId: "fam-1")
        let unchecked = try await repo.fetchUncheckedShoppingItems(listIds: ["l1"])
        let none = try await repo.fetchUncheckedShoppingItems(listIds: [])
        XCTAssertEqual(lists.first?.title, "Groceries")
        XCTAssertEqual(unchecked.first?.item, "Apples")
        XCTAssertTrue(none.isEmpty)
        let itemRead = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/shopping_items").first)
        XCTAssertTrue(itemRead.hasFilter("checked", "eq.false"))
    }

    // MARK: - Map

    func testLocationReadUpsertAndHide() async throws {
        StubTransport.respond(
            table: "user_locations",
            with: [UserLocationModel(
                userId: "lars",
                familyId: "fam-1",
                lat: 59.9139,
                lng: 10.7522,
                displayName: "Lars",
                visible: true
            )]
        )
        let locations = try await repo.fetchUserLocations(familyId: "fam-1")
        await repo.upsertUserLocation(UserLocationModel(
            userId: "emma",
            familyId: "fam-1",
            lat: 59.9075,
            lng: 10.7531,
            displayName: "Emma",
            visible: true
        ))
        await repo.upsertUserLocation(UserLocationModel(userId: "emma", lat: 59.9, lng: 10.7, displayName: "Emma"))
        await repo.clearUserLocationVisibility(userId: "emma")

        XCTAssertEqual(locations.first?.displayName, "Lars")
        XCTAssertTrue(StubTransport.requests[0].hasFilter("visible", "eq.true"))
        let upserts = StubTransport.requests(to: "/rest/v1/user_locations", method: "POST").map(\.jsonObject)
        XCTAssertEqual(upserts[0]["family_id"] as? String, "fam-1")
        XCTAssertTrue(upserts[1]["family_id"] is NSNull)
        let hide = try XCTUnwrap(StubTransport.requests(to: "/rest/v1/user_locations", method: "PATCH").first)
        XCTAssertEqual(hide.jsonObject["visible"] as? Bool, false)
    }

    // MARK: - Settings mirrors

    func testNotificationPrefsMirrorToTheServerWhenSignedIn() async throws {
        let session = SessionStore.shared
        let originalTheme = session.themeMode
        let originalEnabled = session.notificationsEnabled
        let originalDays = session.notifyDaysBefore
        defer {
            session.setThemeMode(originalTheme)
            session.setNotificationsEnabled(originalEnabled)
            session.setNotifyDaysBefore(originalDays)
        }
        session.signIn(userId: "emma")

        repo.setThemeMode(.dark)
        await repo.setNotificationsEnabled(false)
        await repo.setNotifyDaysBefore(3)
        await repo.syncNotificationPrefsToServer()

        XCTAssertEqual(session.themeMode, .dark)
        let writes = StubTransport.requests(to: "/rest/v1/users", method: "PATCH").map(\.jsonObject)
        XCTAssertEqual(writes.count, 3)
        XCTAssertEqual(writes[0]["notifications_enabled"] as? Bool, false)
        XCTAssertEqual(writes[1]["notify_days_before"] as? Int, 3)
        XCTAssertEqual(writes[2]["notify_days_before"] as? Int, 3)
    }

    func testNotificationPrefsStayLocalWhenSignedOut() async {
        let session = SessionStore.shared
        let originalEnabled = session.notificationsEnabled
        defer { session.setNotificationsEnabled(originalEnabled) }
        session.signOut()
        await repo.setNotificationsEnabled(!originalEnabled)
        XCTAssertTrue(StubTransport.requests.isEmpty)
    }
}
