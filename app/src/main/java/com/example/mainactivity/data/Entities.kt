package com.example.mainactivity.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val password: String,
    val birthday: String = "",
    val mobile: String = "",
    val familyId: Long? = null,
    val avatarColor: Int = 0
)

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val password: String,
    val adminId: Long
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val ownerUserId: Long
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val item: String,
    val checked: Boolean = false
)

@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromDate: String,
    val toDate: String,
    val familyId: Long,
    val week: Int
)

@Entity(tableName = "meal_plan_days")
data class MealPlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealPlanId: Long,
    val day: String,
    val date: String,
    val food: String = ""
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateFrom: String,
    val dateTo: String,
    val timeFrom: String,
    val timeTo: String,
    val userId: Long,
    val activity: String,
    val allDay: Boolean = false
)

@Entity(tableName = "birthdays")
data class BirthdayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    val familyId: Long?,
    val userId: Long?,
    val madeByUserId: Long
)

@Entity(tableName = "wishlists")
data class WishlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: Long,
    val name: String
)

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wishlistId: Long,
    val text: String,
    val checked: Boolean = false,
    val userId: Long
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userFrom: Long,
    val userTo: Long,
    val name: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val userFrom: Long,
    val text: String,
    val sentAt: Long = System.currentTimeMillis()
)
