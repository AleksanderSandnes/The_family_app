package com.example.mainactivity.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EntitiesTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ──────────────────────────────────────────────────────────────
    // 1. UserModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun userModel_roundTrip() {
        val original =
            UserModel(
                id = "u1",
                authId = "auth-uuid",
                name = "Alice",
                email = "alice@example.com",
                birthday = "1990-01-15",
                mobile = "+4712345678",
                familyId = "f1",
                avatarColor = 0xFF6200EE.toInt(),
                avatarUrl = "https://example.com/avatar.png",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<UserModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun userModel_nullableFieldsAreNull() {
        val user = UserModel(id = "u2", name = "Bob", email = "bob@example.com")
        assertNull(user.familyId)
        assertNull(user.avatarUrl)
        assertNull(user.authId)
    }

    @Test
    fun userModel_serialNameMapping() {
        val jsonStr =
            """
            {
              "id": "u3",
              "auth_id": "auth-abc",
              "name": "Carol",
              "email": "carol@x.com",
              "birthday": "",
              "mobile": "",
              "family_id": "f2",
              "avatar_color": 123,
              "avatar_url": "https://x.com/img.png"
            }
            """.trimIndent()
        val user = json.decodeFromString<UserModel>(jsonStr)
        assertEquals("auth-abc", user.authId)
        assertEquals("f2", user.familyId)
        assertEquals(123, user.avatarColor)
        assertEquals("https://x.com/img.png", user.avatarUrl)
    }

    @Test
    fun userModel_defaultValues() {
        val user = UserModel()
        assertEquals("", user.id)
        assertEquals("", user.name)
        assertEquals("", user.email)
        assertEquals("", user.birthday)
        assertEquals("", user.mobile)
        assertEquals(0, user.avatarColor)
    }

    // ──────────────────────────────────────────────────────────────
    // 2. FamilyModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun familyModel_roundTrip() {
        val original = FamilyModel(id = "f1", name = "The Smiths", joinCode = "ABC123", adminId = "u1")
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<FamilyModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun familyModel_nullAdminId() {
        val family = FamilyModel(id = "f2", name = "Guests", joinCode = "XYZ")
        assertNull(family.adminId)
    }

    @Test
    fun familyModel_serialNameMapping() {
        val jsonStr = """{"id":"f1","name":"Family","join_code":"J1","admin_id":"u9"}"""
        val family = json.decodeFromString<FamilyModel>(jsonStr)
        assertEquals("J1", family.joinCode)
        assertEquals("u9", family.adminId)
    }

    // ──────────────────────────────────────────────────────────────
    // 3. ShoppingListModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun shoppingListModel_roundTrip() {
        val original =
            ShoppingListModel(
                id = "sl1",
                title = "Weekly Shop",
                ownerUserId = "u1",
                familyId = "f1",
                icon = "shopping_cart",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ShoppingListModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun shoppingListModel_defaultIcon() {
        val list = ShoppingListModel(id = "sl2", title = "Extras", ownerUserId = "u1")
        assertEquals("shopping_cart", list.icon)
    }

    @Test
    fun shoppingListModel_serialNameMapping() {
        val jsonStr = """{"id":"sl1","title":"Groceries","owner_user_id":"u1","family_id":"f1","icon":"shopping_cart"}"""
        val list = json.decodeFromString<ShoppingListModel>(jsonStr)
        assertEquals("u1", list.ownerUserId)
        assertEquals("f1", list.familyId)
    }

    // ──────────────────────────────────────────────────────────────
    // 4. ShoppingItemModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun shoppingItemModel_roundTrip() {
        val original = ShoppingItemModel(id = "si1", listId = "sl1", item = "Milk", checked = true)
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ShoppingItemModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun shoppingItemModel_defaultCheckedFalse() {
        val item = ShoppingItemModel(id = "si2", listId = "sl1", item = "Eggs")
        assertFalse(item.checked)
    }

    @Test
    fun shoppingItemModel_serialNameMapping() {
        val jsonStr = """{"id":"si1","list_id":"sl99","item":"Bread","checked":false}"""
        val item = json.decodeFromString<ShoppingItemModel>(jsonStr)
        assertEquals("sl99", item.listId)
        assertEquals("Bread", item.item)
    }

    // ──────────────────────────────────────────────────────────────
    // 5. MealPlanModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun mealPlanModel_roundTrip() {
        val original =
            MealPlanModel(
                id = "mp1",
                familyId = "f1",
                fromDate = "2025-01-06",
                toDate = "2025-01-12",
                week = 2,
                name = "Week 2",
                icon = "restaurant",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<MealPlanModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun mealPlanModel_defaultIconAndWeek() {
        val plan = MealPlanModel(id = "mp2", familyId = "f1")
        assertEquals("restaurant", plan.icon)
        assertEquals(0, plan.week)
    }

    @Test
    fun mealPlanModel_serialNameMapping() {
        val jsonStr = """{"id":"mp1","family_id":"f1","from_date":"2025-01-06","to_date":"2025-01-12","week":1,"name":"Wk1","icon":"restaurant"}"""
        val plan = json.decodeFromString<MealPlanModel>(jsonStr)
        assertEquals("f1", plan.familyId)
        assertEquals("2025-01-06", plan.fromDate)
        assertEquals("2025-01-12", plan.toDate)
    }

    // ──────────────────────────────────────────────────────────────
    // 6. MealPlanDayModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun mealPlanDayModel_roundTrip() {
        val original =
            MealPlanDayModel(
                id = "mpd1",
                mealPlanId = "mp1",
                day = "Monday",
                date = "2025-01-06",
                food = "Pasta",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<MealPlanDayModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun mealPlanDayModel_serialNameMapping() {
        val jsonStr = """{"id":"mpd1","meal_plan_id":"mp99","day":"Tuesday","date":"2025-01-07","food":"Pizza"}"""
        val day = json.decodeFromString<MealPlanDayModel>(jsonStr)
        assertEquals("mp99", day.mealPlanId)
        assertEquals("Pizza", day.food)
    }

    // ──────────────────────────────────────────────────────────────
    // 7. CalendarEventModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun calendarEventModel_roundTrip() {
        val original =
            CalendarEventModel(
                id = "ce1",
                userId = "u1",
                familyId = "f1",
                dateFrom = "2025-06-01",
                dateTo = "2025-06-01",
                timeFrom = "09:00",
                timeTo = "10:00",
                activity = "Team meeting",
                allDay = false,
                icon = "schedule",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<CalendarEventModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun calendarEventModel_defaultAllDayFalse() {
        val event = CalendarEventModel(id = "ce2", userId = "u1", activity = "Dentist")
        assertFalse(event.allDay)
        assertEquals("schedule", event.icon)
    }

    @Test
    fun calendarEventModel_nullableFamilyId() {
        val event = CalendarEventModel(id = "ce3", userId = "u1", activity = "Personal")
        assertNull(event.familyId)
    }

    @Test
    fun calendarEventModel_serialNameMapping() {
        val jsonStr =
            """
            {
              "id":"ce1","user_id":"u1","family_id":"f1",
              "date_from":"2025-06-01","date_to":"2025-06-01",
              "time_from":"08:00","time_to":"09:00",
              "activity":"Standup","all_day":true,"icon":"event"
            }
            """.trimIndent()
        val event = json.decodeFromString<CalendarEventModel>(jsonStr)
        assertEquals("u1", event.userId)
        assertEquals("2025-06-01", event.dateFrom)
        assertEquals("2025-06-01", event.dateTo)
        assertEquals("08:00", event.timeFrom)
        assertEquals("09:00", event.timeTo)
        assertEquals(true, event.allDay)
    }

    // ──────────────────────────────────────────────────────────────
    // 8. BirthdayModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun birthdayModel_roundTrip() {
        val original =
            BirthdayModel(
                id = "b1",
                name = "Grandma",
                date = "1945-03-22",
                familyId = "f1",
                userId = "u2",
                madeByUserId = "u1",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<BirthdayModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun birthdayModel_nullableFields() {
        val birthday = BirthdayModel(id = "b2", name = "Friend", date = "2000-07-04", madeByUserId = "u1")
        assertNull(birthday.familyId)
        assertNull(birthday.userId)
    }

    @Test
    fun birthdayModel_serialNameMapping() {
        val jsonStr = """{"id":"b1","name":"Dad","date":"1960-05-10","family_id":"f1","user_id":"u3","made_by_user_id":"u1"}"""
        val birthday = json.decodeFromString<BirthdayModel>(jsonStr)
        assertEquals("f1", birthday.familyId)
        assertEquals("u3", birthday.userId)
        assertEquals("u1", birthday.madeByUserId)
    }

    // ──────────────────────────────────────────────────────────────
    // 9. WishlistModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun wishlistModel_roundTrip() {
        val original =
            WishlistModel(
                id = "wl1",
                ownerUserId = "u1",
                familyId = "f1",
                name = "Birthday Wishes",
                icon = "card_giftcard",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<WishlistModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun wishlistModel_defaultIcon() {
        val wishlist = WishlistModel(id = "wl2", ownerUserId = "u1", name = "My List")
        assertEquals("card_giftcard", wishlist.icon)
    }

    @Test
    fun wishlistModel_serialNameMapping() {
        val jsonStr = """{"id":"1","owner_user_id":"u1","family_id":"f1","name":"Gifts","icon":"star"}"""
        val wishlist = json.decodeFromString<WishlistModel>(jsonStr)
        assertEquals("u1", wishlist.ownerUserId)
        assertEquals("star", wishlist.icon)
    }

    // ──────────────────────────────────────────────────────────────
    // 10. WishModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun wishModel_roundTrip() {
        val original =
            WishModel(
                id = "w1",
                wishlistId = "wl1",
                userId = "u1",
                text = "New bike",
                checked = false,
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<WishModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun wishModel_defaultCheckedFalse() {
        val wish = WishModel(id = "w2", wishlistId = "wl1", userId = "u1", text = "Book")
        assertFalse(wish.checked)
    }

    @Test
    fun wishModel_serialNameMapping() {
        val jsonStr = """{"id":"w1","wishlist_id":"wl9","user_id":"u9","text":"Camera","checked":true}"""
        val wish = json.decodeFromString<WishModel>(jsonStr)
        assertEquals("wl9", wish.wishlistId)
        assertEquals("u9", wish.userId)
        assertEquals(true, wish.checked)
    }

    // ──────────────────────────────────────────────────────────────
    // 11. ConversationModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun conversationModel_roundTrip() {
        val original =
            ConversationModel(
                id = "c1",
                userFrom = "u1",
                userTo = "u2",
                name = "Chat with Bob",
                familyId = "f1",
                imageUri = "https://example.com/group.png",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ConversationModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun conversationModel_defaultName() {
        val convo = ConversationModel(id = "c2", userFrom = "u1")
        assertEquals("", convo.name)
        assertNull(convo.userTo)
        assertNull(convo.familyId)
        assertNull(convo.imageUri)
    }

    @Test
    fun conversationModel_serialNameMapping() {
        val jsonStr = """{"id":"c1","user_from":"u1","user_to":"u2","name":"Test","family_id":"f1","image_uri":"img.png"}"""
        val convo = json.decodeFromString<ConversationModel>(jsonStr)
        assertEquals("u1", convo.userFrom)
        assertEquals("u2", convo.userTo)
        assertEquals("f1", convo.familyId)
        assertEquals("img.png", convo.imageUri)
    }

    // ──────────────────────────────────────────────────────────────
    // 12. UserLocationModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun userLocationModel_roundTrip() {
        val original =
            UserLocationModel(
                userId = "u1",
                familyId = "f1",
                lat = 59.9139,
                lng = 10.7522,
                displayName = "Alice",
                visible = true,
                updatedAt = "2025-06-25T12:00:00Z",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<UserLocationModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun userLocationModel_defaultValues() {
        val loc = UserLocationModel()
        assertEquals("", loc.userId)
        assertEquals(0.0, loc.lat, 0.0)
        assertEquals(0.0, loc.lng, 0.0)
        assertEquals("", loc.displayName)
        assertFalse(loc.visible)
        assertNull(loc.familyId)
        assertNull(loc.updatedAt)
    }

    @Test
    fun userLocationModel_serialNameMapping() {
        val jsonStr = """{"user_id":"u1","family_id":"f1","lat":59.9,"lng":10.7,"display_name":"Bob","visible":true,"updated_at":"2025-01-01T00:00:00Z"}"""
        val loc = json.decodeFromString<UserLocationModel>(jsonStr)
        assertEquals("u1", loc.userId)
        assertEquals("f1", loc.familyId)
        assertEquals("Bob", loc.displayName)
        assertEquals("2025-01-01T00:00:00Z", loc.updatedAt)
    }

    // ──────────────────────────────────────────────────────────────
    // 13. ConversationParticipantModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun conversationParticipantModel_roundTrip() {
        val original =
            ConversationParticipantModel(
                id = "cp1",
                conversationId = "c1",
                userId = "u1",
                joinedAt = "2025-01-01T10:00:00Z",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<ConversationParticipantModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun conversationParticipantModel_serialNameMapping() {
        val jsonStr = """{"id":"cp1","conversation_id":"c99","user_id":"u5","joined_at":"2025-06-01T00:00:00Z"}"""
        val participant = json.decodeFromString<ConversationParticipantModel>(jsonStr)
        assertEquals("c99", participant.conversationId)
        assertEquals("u5", participant.userId)
        assertEquals("2025-06-01T00:00:00Z", participant.joinedAt)
    }

    // ──────────────────────────────────────────────────────────────
    // 14. MessageModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun messageModel_roundTrip() {
        val original =
            MessageModel(
                id = "m1",
                conversationId = "c1",
                userFrom = "u1",
                text = "Hello!",
                sentAt = "2025-06-25T09:00:00Z",
                replyToId = null,
                messageType = "text",
                mediaUrl = null,
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<MessageModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun messageModel_defaultValues() {
        val msg = MessageModel()
        assertEquals("text", msg.messageType)
        assertNull(msg.replyToId)
        assertNull(msg.mediaUrl)
    }

    @Test
    fun messageModel_nullableReplyToId() {
        val msg =
            MessageModel(
                id = "m2",
                conversationId = "c1",
                userFrom = "u1",
                text = "Reply",
                sentAt = "2025-01-01T00:00:00Z",
                replyToId = "m1",
            )
        assertEquals("m1", msg.replyToId)
    }

    @Test
    fun messageModel_serialNameMapping() {
        val jsonStr = """{"id":"m1","conversation_id":"c1","user_from":"u1","text":"Hi","sent_at":"2025-01-01T00:00:00Z","reply_to_id":"m0","message_type":"image","media_url":"https://img.com/x.jpg"}"""
        val msg = json.decodeFromString<MessageModel>(jsonStr)
        assertEquals("c1", msg.conversationId)
        assertEquals("u1", msg.userFrom)
        assertEquals("2025-01-01T00:00:00Z", msg.sentAt)
        assertEquals("m0", msg.replyToId)
        assertEquals("image", msg.messageType)
        assertEquals("https://img.com/x.jpg", msg.mediaUrl)
    }

    // ──────────────────────────────────────────────────────────────
    // 15. MessageReactionModel
    // ──────────────────────────────────────────────────────────────

    @Test
    fun messageReactionModel_roundTrip() {
        val original =
            MessageReactionModel(
                id = "r1",
                messageId = "m1",
                conversationId = "c1",
                userId = "u1",
                emoji = "👍",
                createdAt = "2025-06-25T10:00:00Z",
            )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<MessageReactionModel>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun messageReactionModel_serialNameMapping() {
        val jsonStr = """{"id":"r1","message_id":"m9","conversation_id":"c9","user_id":"u9","emoji":"❤️","created_at":"2025-06-25T10:00:00Z"}"""
        val reaction = json.decodeFromString<MessageReactionModel>(jsonStr)
        assertEquals("m9", reaction.messageId)
        assertEquals("c9", reaction.conversationId)
        assertEquals("u9", reaction.userId)
        assertEquals("2025-06-25T10:00:00Z", reaction.createdAt)
    }

    // ──────────────────────────────────────────────────────────────
    // 16. ConversationWithPreview (not @Serializable — construction only)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun conversationWithPreview_construction() {
        val conversation = ConversationModel(id = "c1", userFrom = "u1", name = "Family Group")
        val lastMessage =
            MessageModel(
                id = "m1",
                conversationId = "c1",
                userFrom = "u2",
                text = "Hey!",
                sentAt = "2025-06-25T08:00:00Z",
            )
        val participants =
            listOf(
                UserModel(id = "u1", name = "Alice", email = "alice@example.com"),
                UserModel(id = "u2", name = "Bob", email = "bob@example.com"),
            )
        val preview =
            ConversationWithPreview(
                conversation = conversation,
                lastMessage = lastMessage,
                lastSenderName = "Bob",
                unreadCount = 3,
                participants = participants,
            )

        assertEquals(conversation, preview.conversation)
        assertEquals(lastMessage, preview.lastMessage)
        assertEquals("Bob", preview.lastSenderName)
        assertEquals(3, preview.unreadCount)
        assertEquals(2, preview.participants.size)
    }

    @Test
    fun conversationWithPreview_nullableLastMessageAndSender() {
        val convo = ConversationModel(id = "c2", userFrom = "u1")
        val preview =
            ConversationWithPreview(
                conversation = convo,
                lastMessage = null,
                lastSenderName = null,
                unreadCount = 0,
                participants = emptyList(),
            )

        assertNull(preview.lastMessage)
        assertNull(preview.lastSenderName)
        assertEquals(0, preview.unreadCount)
        assertEquals(0, preview.participants.size)
    }
}
