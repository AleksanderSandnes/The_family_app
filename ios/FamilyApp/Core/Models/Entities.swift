// Data models for the Postgres tables (Android parity).
// CodingKeys map to the snake_case columns; keep both platforms in sync.
import Foundation

struct UserModel: Codable, Identifiable, Hashable {
    var id = ""
    var authId: String?
    var name = ""
    var email = ""
    var birthday = ""
    var mobile = ""
    var familyId: String?
    var avatarColor = 0
    var avatarUrl: String?
    var lastActiveAt: String?
    // Server mirror of the client notification settings, so server-side push
    // (daily reminders) can honour each user's preference.
    var notificationsEnabled = true
    var notifyDaysBefore = 1

    enum CodingKeys: String, CodingKey {
        case id, name, email, birthday, mobile
        case authId = "auth_id"
        case familyId = "family_id"
        case avatarColor = "avatar_color"
        case avatarUrl = "avatar_url"
        case lastActiveAt = "last_active_at"
        case notificationsEnabled = "notifications_enabled"
        case notifyDaysBefore = "notify_days_before"
    }
}

struct FamilyModel: Codable, Identifiable, Hashable {
    var id = ""
    var name = ""
    var joinCode = ""
    var adminId: String?
    var photoUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, name
        case joinCode = "join_code"
        case adminId = "admin_id"
        case photoUrl = "photo_url"
    }
}

/// Directional family relation ("relative to each viewer"): fromUserId's relation TO toUserId.
struct FamilyRelationModel: Codable, Identifiable, Hashable {
    var id = ""
    var familyId = ""
    var fromUserId = ""
    var toUserId = ""
    var relation = ""

    enum CodingKeys: String, CodingKey {
        case id, relation
        case familyId = "family_id"
        case fromUserId = "from_user_id"
        case toUserId = "to_user_id"
    }
}

struct ShoppingListModel: Codable, Identifiable, Hashable {
    var id = ""
    var title = ""
    var ownerUserId = ""
    var familyId: String?
    var icon = "shopping_cart"
    var color: Int?

    enum CodingKeys: String, CodingKey {
        case id, title, icon, color
        case ownerUserId = "owner_user_id"
        case familyId = "family_id"
    }
}

struct ShoppingItemModel: Codable, Identifiable, Hashable {
    var id = ""
    var listId = ""
    var item = ""
    var checked = false

    enum CodingKeys: String, CodingKey {
        case id, item, checked
        case listId = "list_id"
    }
}

struct MealPlanModel: Codable, Identifiable, Hashable {
    var id = ""
    var familyId = ""
    var fromDate = ""
    var toDate = ""
    var week = 0
    var name = ""
    var icon = "restaurant"
    var color: Int?
    // Creator (add_destructive_action_gating.sql). Nil on legacy rows → admin-only delete.
    var createdBy: String?

    enum CodingKeys: String, CodingKey {
        case id, week, name, icon, color
        case familyId = "family_id"
        case fromDate = "from_date"
        case toDate = "to_date"
        case createdBy = "created_by"
    }
}

struct MealPlanDayModel: Codable, Identifiable, Hashable {
    var id = ""
    var mealPlanId = ""
    var day = ""
    var date = ""
    var food = ""

    enum CodingKeys: String, CodingKey {
        case id, day, date, food
        case mealPlanId = "meal_plan_id"
    }
}

struct CalendarEventModel: Codable, Identifiable, Hashable {
    var id = ""
    var userId = ""
    var familyId: String?
    var dateFrom = ""
    var dateTo = ""
    var timeFrom = ""
    var timeTo = ""
    var activity = ""
    var allDay = false
    var icon = "schedule"
    var isPrivate = false
    var color: Int?
    /// Family member ids the creator is going to this event with.
    var attendeeIds: [String] = []

    enum CodingKeys: String, CodingKey {
        case id, activity, icon, color
        case userId = "user_id"
        case familyId = "family_id"
        case dateFrom = "date_from"
        case dateTo = "date_to"
        case timeFrom = "time_from"
        case timeTo = "time_to"
        case attendeeIds = "attendee_ids"
        case allDay = "all_day"
        case isPrivate = "is_private"
    }
}

struct BirthdayModel: Codable, Identifiable, Hashable {
    var id = ""
    var name = ""
    var date = ""
    var familyId: String?
    var userId: String?
    var madeByUserId = ""
    var icon = "cake"
    var color: Int?

    enum CodingKeys: String, CodingKey {
        case id, name, date, icon, color
        case familyId = "family_id"
        case userId = "user_id"
        case madeByUserId = "made_by_user_id"
    }
}

struct WishlistModel: Codable, Identifiable, Hashable {
    var id = ""
    var ownerUserId = ""
    var familyId: String?
    var name = ""
    var icon = "card_giftcard"
    var color: Int?
    /// Not a DB column — resolved client-side from family members.
    var ownerName = ""

    enum CodingKeys: String, CodingKey {
        case id, name, icon, color
        case ownerUserId = "owner_user_id"
        case familyId = "family_id"
    }
}

struct WishModel: Codable, Identifiable, Hashable {
    var id = ""
    var wishlistId = ""
    var userId = ""
    var text = ""
    var checked = false
    var link: String?
    var price: String?
    var imageUrl: String?
    var description: String?

    enum CodingKeys: String, CodingKey {
        case id, text, checked, link, price, description
        case wishlistId = "wishlist_id"
        case userId = "user_id"
        case imageUrl = "image_url"
    }
}

struct WishReservationModel: Codable, Identifiable, Hashable {
    var id = ""
    var wishId = ""
    var reservedBy = ""
    var createdAt = ""

    enum CodingKeys: String, CodingKey {
        case id
        case wishId = "wish_id"
        case reservedBy = "reserved_by"
        case createdAt = "created_at"
    }
}

struct ConversationModel: Codable, Identifiable, Hashable {
    var id = ""
    var userFrom = ""
    var userTo: String?
    var name = ""
    var familyId: String?
    var imageUri: String?

    enum CodingKeys: String, CodingKey {
        case id, name
        case userFrom = "user_from"
        case userTo = "user_to"
        case familyId = "family_id"
        case imageUri = "image_uri"
    }
}

struct UserLocationModel: Codable, Hashable {
    var userId = ""
    var familyId: String?
    var lat: Double = 0
    var lng: Double = 0
    var displayName = ""
    var visible = false
    var updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case lat, lng, visible
        case userId = "user_id"
        case familyId = "family_id"
        case displayName = "display_name"
        case updatedAt = "updated_at"
    }
}

struct ConversationParticipantModel: Codable, Identifiable, Hashable {
    var id = ""
    var conversationId = ""
    var userId = ""
    var joinedAt = ""
    var lastReadAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case conversationId = "conversation_id"
        case userId = "user_id"
        case joinedAt = "joined_at"
        case lastReadAt = "last_read_at"
    }
}

struct MessageModel: Codable, Identifiable, Hashable {
    var id = ""
    var conversationId = ""
    var userFrom = ""
    var text = ""
    var sentAt = ""
    var replyToId: String?
    var messageType = "text"
    var mediaUrl: String?
    // Set when the sender edits the message (add_message_edit_delete.sql).
    var editedAt: String?

    enum CodingKeys: String, CodingKey {
        case id, text
        case conversationId = "conversation_id"
        case userFrom = "user_from"
        case sentAt = "sent_at"
        case replyToId = "reply_to_id"
        case messageType = "message_type"
        case mediaUrl = "media_url"
        case editedAt = "edited_at"
    }
}

struct MessageReactionModel: Codable, Identifiable, Hashable {
    var id = ""
    var messageId = ""
    var conversationId = ""
    var userId = ""
    var emoji = ""
    var createdAt = ""

    enum CodingKeys: String, CodingKey {
        case id, emoji
        case messageId = "message_id"
        case conversationId = "conversation_id"
        case userId = "user_id"
        case createdAt = "created_at"
    }
}

/// UI aggregate for the conversation list — not a DB row.
struct ConversationWithPreview: Identifiable, Hashable {
    var conversation: ConversationModel
    var lastMessage: MessageModel?
    var lastSenderName: String?
    var unreadCount: Int
    var participants: [UserModel]

    var id: String {
        conversation.id
    }
}
