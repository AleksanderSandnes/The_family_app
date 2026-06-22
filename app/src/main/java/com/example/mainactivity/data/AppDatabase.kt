package com.example.mainactivity.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        FamilyEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        MealPlanEntity::class,
        MealPlanDayEntity::class,
        CalendarEventEntity::class,
        BirthdayEntity::class,
        WishlistEntity::class,
        WishEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun familyDao(): FamilyDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun calendarDao(): CalendarDao
    abstract fun birthdayDao(): BirthdayDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "the_family_app.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
