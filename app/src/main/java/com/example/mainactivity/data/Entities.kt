package com.example.mainactivity.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserModel(
    val id: String = "",
    @SerialName("auth_id") val authId: String? = null,
    val name: String = "",
    val email: String = "",
    val birthday: String = "",
    val mobile: String = "",
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("avatar_color") val avatarColor: Int = 0,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class FamilyModel(
    val id: String = "",
    val name: String = "",
    @SerialName("join_code") val joinCode: String = "",
    @SerialName("admin_id") val adminId: String? = null,
)

@Serializable
data class ShoppingListModel(
    val id: String = "",
    val title: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    @SerialName("family_id") val familyId: String? = null,
    val icon: String = "shopping_cart",
)

@Serializable
data class ShoppingItemModel(
    val id: String = "",
    @SerialName("list_id") val listId: String = "",
    val item: String = "",
    val checked: Boolean = false,
)

@Serializable
data class MealPlanModel(
    val id: String = "",
    @SerialName("family_id") val familyId: String = "",
    @SerialName("from_date") val fromDate: String = "",
    @SerialName("to_date") val toDate: String = "",
    val week: Int = 0,
    val name: String = "",
    val icon: String = "restaurant",
)

@Serializable
data class MealPlanDayModel(
    val id: String = "",
    @SerialName("meal_plan_id") val mealPlanId: String = "",
    val day: String = "",
    val date: String = "",
    val food: String = "",
)

@Serializable
data class CalendarEventModel(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("date_from") val dateFrom: String = "",
    @SerialName("date_to") val dateTo: String = "",
    @SerialName("time_from") val timeFrom: String = "",
    @SerialName("time_to") val timeTo: String = "",
    val activity: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    val icon: String = "schedule",
)

@Serializable
data class BirthdayModel(
    val id: String = "",
    val name: String = "",
    val date: String = "",
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("made_by_user_id") val madeByUserId: String = "",
)

@Serializable
data class WishlistModel(
    val id: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    @SerialName("family_id") val familyId: String? = null,
    val name: String = "",
)

@Serializable
data class WishModel(
    val id: String = "",
    @SerialName("wishlist_id") val wishlistId: String = "",
    @SerialName("user_id") val userId: String = "",
    val text: String = "",
    val checked: Boolean = false,
)

@Serializable
data class ConversationModel(
    val id: String = "",
    @SerialName("user_from") val userFrom: String = "",
    @SerialName("user_to") val userTo: String? = null,
    val name: String = "",
    @SerialName("family_id") val familyId: String? = null,
    @SerialName("image_uri") val imageUri: String? = null,
)

@Serializable
data class UserLocationModel(
    @SerialName("user_id") val userId: String = "",
    @SerialName("family_id") val familyId: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("display_name") val displayName: String = "",
    val visible: Boolean = false,
)

@Serializable
data class ConversationParticipantModel(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("joined_at") val joinedAt: String = "",
)

@Serializable
data class MessageModel(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_from") val userFrom: String = "",
    val text: String = "",
    @SerialName("sent_at") val sentAt: String = "",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
)

@Serializable
data class MessageReactionModel(
    val id: String = "",
    @SerialName("message_id") val messageId: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String = "",
    val emoji: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

data class ConversationWithPreview(
    val conversation: ConversationModel,
    val lastMessage: MessageModel?,
    val lastSenderName: String?,
    val unreadCount: Int,
    val participants: List<UserModel>,
)
