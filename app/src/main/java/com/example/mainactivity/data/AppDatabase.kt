package com.example.mainactivity.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN icon TEXT NOT NULL DEFAULT 'schedule'")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN imageUri TEXT")
    }
}

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
    version = 6,
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
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
    }
}
