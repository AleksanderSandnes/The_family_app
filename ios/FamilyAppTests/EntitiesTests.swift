// Decoding tests for Entities.swift — verify the snake_case CodingKeys match the
// Postgres columns exactly (mirrors EntitiesTest.kt).
import XCTest
@testable import FamilyApp

final class EntitiesTests: XCTestCase {
    private let decoder = JSONDecoder()

    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try decoder.decode(T.self, from: Data(json.utf8))
    }

    func testUserModelDecodesSnakeCaseColumns() throws {
        let json = """
        {
          "id": "u1", "auth_id": "a1", "name": "Alice", "email": "alice@example.com",
          "birthday": "1990-01-01", "mobile": "12345678", "family_id": "f1",
          "avatar_color": -1023342, "avatar_url": null, "last_active_at": "2026-07-05T10:00:00Z",
          "notifications_enabled": false, "notify_days_before": 3
        }
        """
        let user = try decode(UserModel.self, json)
        XCTAssertEqual(user.id, "u1")
        XCTAssertEqual(user.authId, "a1")
        XCTAssertEqual(user.familyId, "f1")
        XCTAssertEqual(user.avatarColor, -1023342)
        XCTAssertNil(user.avatarUrl)
        XCTAssertFalse(user.notificationsEnabled)
        XCTAssertEqual(user.notifyDaysBefore, 3)
    }

    func testFamilyModelDecodes() throws {
        let json = """
        {"id": "f1", "name": "Sandnes", "join_code": "ABC123", "admin_id": "u1", "photo_url": null}
        """
        let family = try decode(FamilyModel.self, json)
        XCTAssertEqual(family.joinCode, "ABC123")
        XCTAssertEqual(family.adminId, "u1")
        XCTAssertNil(family.photoUrl)
    }

    func testShoppingModelsDecode() throws {
        let list = try decode(ShoppingListModel.self, """
        {"id": "l1", "title": "Groceries", "owner_user_id": "u1", "family_id": "f1", "icon": "shopping_cart"}
        """)
        XCTAssertEqual(list.ownerUserId, "u1")

        let item = try decode(ShoppingItemModel.self, """
        {"id": "i1", "list_id": "l1", "item": "Milk", "checked": true}
        """)
        XCTAssertEqual(item.listId, "l1")
        XCTAssertTrue(item.checked)
    }

    func testMealPlanModelsDecode() throws {
        let plan = try decode(MealPlanModel.self, """
        {"id": "m1", "family_id": "f1", "from_date": "2026-07-06", "to_date": "2026-07-12",
         "week": 28, "name": "Week 28", "icon": "restaurant"}
        """)
        XCTAssertEqual(plan.fromDate, "2026-07-06")
        XCTAssertEqual(plan.week, 28)

        let day = try decode(MealPlanDayModel.self, """
        {"id": "d1", "meal_plan_id": "m1", "day": "Monday", "date": "2026-07-06", "food": "Tacos"}
        """)
        XCTAssertEqual(day.mealPlanId, "m1")
    }

    func testCalendarEventModelDecodes() throws {
        let event = try decode(CalendarEventModel.self, """
        {"id": "e1", "user_id": "u1", "family_id": "f1", "date_from": "2026-07-06",
         "date_to": "2026-07-06", "time_from": "18:00", "time_to": "19:00",
         "activity": "Football", "all_day": false, "icon": "schedule"}
        """)
        XCTAssertEqual(event.dateFrom, "2026-07-06")
        XCTAssertFalse(event.allDay)
    }

    func testBirthdayModelDecodes() throws {
        let birthday = try decode(BirthdayModel.self, """
        {"id": "b1", "name": "Alice", "date": "1990-01-01", "family_id": "f1",
         "user_id": "u1", "made_by_user_id": "u1"}
        """)
        XCTAssertEqual(birthday.madeByUserId, "u1")
    }

    func testWishModelsDecodeAndOwnerNameIsTransient() throws {
        let wishlist = try decode(WishlistModel.self, """
        {"id": "w1", "owner_user_id": "u1", "family_id": "f1", "name": "Christmas", "icon": "card_giftcard"}
        """)
        // ownerName is not a DB column — must default, never decode.
        XCTAssertEqual(wishlist.ownerName, "")

        let wish = try decode(WishModel.self, """
        {"id": "wi1", "wishlist_id": "w1", "user_id": "u1", "text": "Lego",
         "checked": false, "link": "https://example.com", "price": "499", "image_url": null}
        """)
        XCTAssertEqual(wish.link, "https://example.com")
        XCTAssertNil(wish.imageUrl)

        let reservation = try decode(WishReservationModel.self, """
        {"id": "r1", "wish_id": "wi1", "reserved_by": "u2", "created_at": "2026-07-05T10:00:00Z"}
        """)
        XCTAssertEqual(reservation.reservedBy, "u2")
    }

    func testWishlistEncodingOmitsOwnerName() throws {
        var wishlist = WishlistModel()
        wishlist.ownerName = "should-not-serialize"
        let data = try JSONEncoder().encode(wishlist)
        let json = String(decoding: data, as: UTF8.self)
        XCTAssertFalse(json.contains("should-not-serialize"))
        XCTAssertFalse(json.contains("ownerName"))
    }

    func testChatModelsDecode() throws {
        let conversation = try decode(ConversationModel.self, """
        {"id": "c1", "user_from": "u1", "user_to": null, "name": "Family",
         "family_id": "f1", "image_uri": null}
        """)
        XCTAssertNil(conversation.userTo)

        let participant = try decode(ConversationParticipantModel.self, """
        {"id": "p1", "conversation_id": "c1", "user_id": "u1",
         "joined_at": "2026-07-05T10:00:00Z", "last_read_at": null}
        """)
        XCTAssertNil(participant.lastReadAt)

        let message = try decode(MessageModel.self, """
        {"id": "m1", "conversation_id": "c1", "user_from": "u1", "text": "hi",
         "sent_at": "2026-07-05T10:00:00Z", "reply_to_id": null,
         "message_type": "text", "media_url": null}
        """)
        XCTAssertEqual(message.messageType, "text")

        let reaction = try decode(MessageReactionModel.self, """
        {"id": "r1", "message_id": "m1", "conversation_id": "c1", "user_id": "u1",
         "emoji": "❤️", "created_at": "2026-07-05T10:00:00Z"}
        """)
        XCTAssertEqual(reaction.emoji, "❤️")
    }

    func testUserLocationModelDecodes() throws {
        let location = try decode(UserLocationModel.self, """
        {"user_id": "u1", "family_id": "f1", "lat": 58.85, "lng": 5.73,
         "display_name": "Alice", "visible": true, "updated_at": "2026-07-05T10:00:00Z"}
        """)
        XCTAssertEqual(location.lat, 58.85, accuracy: 0.0001)
        XCTAssertTrue(location.visible)
    }
}
