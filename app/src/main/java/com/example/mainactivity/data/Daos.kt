package com.example.mainactivity.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE familyId = :familyId ORDER BY name")
    fun membersOfFamily(familyId: Long): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    suspend fun countByEmail(email: String): Int
}

@Dao
interface FamilyDao {
    @Insert
    suspend fun insert(family: FamilyEntity): Long

    @Update
    suspend fun update(family: FamilyEntity)

    @Query("SELECT * FROM families WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<FamilyEntity?>

    @Query("SELECT * FROM families WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FamilyEntity?

    @Query("SELECT * FROM families WHERE name = :name AND password = :password LIMIT 1")
    suspend fun authenticate(name: String, password: String): FamilyEntity?
}

@Dao
interface ShoppingDao {
    @Insert suspend fun insertList(list: ShoppingListEntity): Long
    @Insert suspend fun insertItem(item: ShoppingItemEntity): Long
    @Update suspend fun updateItem(item: ShoppingItemEntity)
    @Delete suspend fun deleteList(list: ShoppingListEntity)
    @Delete suspend fun deleteItem(item: ShoppingItemEntity)

    @Query("SELECT * FROM shopping_lists WHERE ownerUserId = :userId ORDER BY id DESC")
    fun listsForUser(userId: Long): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY checked, id")
    fun itemsForList(listId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    fun observeList(id: Long): Flow<ShoppingListEntity?>
}

@Dao
interface MealPlanDao {
    @Insert suspend fun insertPlan(plan: MealPlanEntity): Long
    @Insert suspend fun insertDay(day: MealPlanDayEntity): Long
    @Update suspend fun updateDay(day: MealPlanDayEntity)
    @Delete suspend fun deletePlan(plan: MealPlanEntity)

    @Query("SELECT * FROM meal_plans WHERE familyId = :familyId ORDER BY id DESC")
    fun plansForFamily(familyId: Long): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan_days WHERE mealPlanId = :planId ORDER BY id")
    fun daysForPlan(planId: Long): Flow<List<MealPlanDayEntity>>

    @Query("SELECT * FROM meal_plans WHERE id = :id LIMIT 1")
    fun observePlan(id: Long): Flow<MealPlanEntity?>
}

@Dao
interface CalendarDao {
    @Insert suspend fun insert(event: CalendarEventEntity): Long
    @Update suspend fun update(event: CalendarEventEntity)
    @Delete suspend fun delete(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events WHERE userId IN (:userIds) ORDER BY dateFrom, timeFrom")
    fun eventsForUsers(userIds: List<Long>): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CalendarEventEntity?
}

@Dao
interface BirthdayDao {
    @Insert suspend fun insert(birthday: BirthdayEntity): Long
    @Update suspend fun update(birthday: BirthdayEntity)
    @Delete suspend fun delete(birthday: BirthdayEntity)

    @Query("SELECT * FROM birthdays WHERE familyId = :familyId OR madeByUserId = :userId ORDER BY date")
    fun birthdaysFor(familyId: Long?, userId: Long): Flow<List<BirthdayEntity>>

    @Query("SELECT * FROM birthdays WHERE madeByUserId = :userId ORDER BY date")
    fun birthdaysForUser(userId: Long): Flow<List<BirthdayEntity>>
}

@Dao
interface WishlistDao {
    @Insert suspend fun insertWishlist(wishlist: WishlistEntity): Long
    @Insert suspend fun insertWish(wish: WishEntity): Long
    @Update suspend fun updateWish(wish: WishEntity)
    @Delete suspend fun deleteWishlist(wishlist: WishlistEntity)
    @Delete suspend fun deleteWish(wish: WishEntity)

    @Query("SELECT * FROM wishlists WHERE ownerUserId = :userId ORDER BY id DESC")
    fun wishlistsForUser(userId: Long): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishes WHERE wishlistId = :wishlistId ORDER BY checked, id")
    fun wishesForList(wishlistId: Long): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishlists WHERE id = :id LIMIT 1")
    fun observeWishlist(id: Long): Flow<WishlistEntity?>
}

@Dao
interface ChatDao {
    @Insert suspend fun insertConversation(conversation: ConversationEntity): Long
    @Insert suspend fun insertMessage(message: MessageEntity): Long
    @Update suspend fun updateConversation(conv: ConversationEntity)

    @Query(
        "SELECT * FROM conversations WHERE userFrom = :userId OR userTo = :userId ORDER BY id DESC"
    )
    fun conversationsForUser(userId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id")
    fun messagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversation(id: Long): Flow<ConversationEntity?>
}
