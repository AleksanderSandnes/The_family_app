package com.sandnes.familyapp.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * DB icon keys → Compose [ImageVector]. The DB stores Material icon keys (`shopping_lists.icon`,
 * `meal_plans.icon`, `wishlists.icon`, `calendar_events.icon`, `birthdays.icon`); this map renders
 * them on Android. Mirrors iOS `IconKeyMap` **exactly** — both apps share these keys via the DB,
 * so the key set here and in `ios/FamilyApp/DesignSystem/IconKeyMap.swift` must stay in sync.
 */
object IconKeyMap {
    private val map: Map<String, ImageVector> =
        mapOf(
            // Shopping list options
            "shopping_cart" to Icons.Filled.ShoppingCart,
            "local_hospital" to Icons.Filled.LocalHospital,
            "celebration" to Icons.Filled.Celebration,
            "favorite" to Icons.Filled.Favorite,
            "star" to Icons.Filled.Star,
            "fitness_center" to Icons.Filled.FitnessCenter,
            "home" to Icons.Filled.Home,
            "pets" to Icons.Filled.Pets,
            "flight" to Icons.Filled.Flight,
            "people" to Icons.Filled.People,
            // Meal plan options
            "restaurant" to Icons.Filled.Restaurant,
            "restaurant_menu" to Icons.Filled.RestaurantMenu,
            "lunch_dining" to Icons.Filled.LunchDining,
            "dinner_dining" to Icons.Filled.DinnerDining,
            "bakery_dining" to Icons.Filled.BakeryDining,
            "local_pizza" to Icons.Filled.LocalPizza,
            "ramen_dining" to Icons.Filled.RamenDining,
            "set_meal" to Icons.Filled.SetMeal,
            "fastfood" to Icons.Filled.Fastfood,
            "cake" to Icons.Filled.Cake,
            "local_cafe" to Icons.Filled.LocalCafe,
            "outdoor_grill" to Icons.Filled.OutdoorGrill,
            "kitchen" to Icons.Filled.Kitchen,
            "egg" to Icons.Filled.Egg,
            "local_bar" to Icons.Filled.LocalBar,
            // Wishlist options
            "card_giftcard" to Icons.Filled.CardGiftcard,
            // Calendar options
            "schedule" to Icons.Filled.Schedule,
            "work" to Icons.Filled.Work,
            "school" to Icons.Filled.School,
            "music_note" to Icons.Filled.MusicNote,
            "wb_sunny" to Icons.Filled.WbSunny,
            "emoji_events" to Icons.Filled.EmojiEvents,
        )

    fun icon(
        key: String?,
        fallback: ImageVector,
    ): ImageVector = key?.let { map[it] } ?: fallback

    fun shopping(key: String?): ImageVector = icon(key, Icons.Filled.ShoppingCart)

    fun meal(key: String?): ImageVector = icon(key, Icons.Filled.Restaurant)

    fun calendar(key: String?): ImageVector = icon(key, Icons.Filled.Schedule)

    fun wishlist(key: String?): ImageVector = icon(key, Icons.Filled.CardGiftcard)

    fun birthday(key: String?): ImageVector = icon(key, Icons.Filled.Cake)
}

/** Icon-picker option lists (DB keys). Mirrors iOS `IconOptions`. */
object IconOptions {
    val shopping =
        listOf(
            "shopping_cart",
            "restaurant",
            "cake",
            "local_hospital",
            "celebration",
            "favorite",
            "star",
            "fitness_center",
            "home",
            "pets",
            "flight",
            "people",
        )

    val meal =
        listOf(
            "restaurant",
            "restaurant_menu",
            "lunch_dining",
            "dinner_dining",
            "bakery_dining",
            "local_pizza",
            "ramen_dining",
            "set_meal",
            "fastfood",
            "cake",
            "local_cafe",
            "outdoor_grill",
            "kitchen",
            "egg",
            "local_bar",
        )

    val calendar =
        listOf(
            "schedule",
            "cake",
            "people",
            "work",
            "school",
            "restaurant",
            "flight",
            "local_hospital",
            "celebration",
            "shopping_cart",
            "music_note",
            "fitness_center",
            "wb_sunny",
            "favorite",
            "star",
            "emoji_events",
        )

    val wishlist =
        listOf(
            "card_giftcard",
            "star",
            "favorite",
            "celebration",
            "flight",
            "home",
            "restaurant",
            "fitness_center",
            "shopping_cart",
            "pets",
            "local_hospital",
            "cake",
        )
}

/** Icon key → stable index 0–5 used to colour event dots/containers. Mirrors iOS. */
@Suppress("MagicNumber") // static icon → colour-index lookup table
fun calendarIconColorIndex(key: String?): Int {
    val indices =
        mapOf(
            "schedule" to 0,
            "cake" to 1,
            "people" to 2,
            "work" to 3,
            "school" to 4,
            "restaurant" to 5,
            "flight" to 0,
            "local_hospital" to 1,
            "celebration" to 2,
            "shopping_cart" to 3,
            "music_note" to 4,
            "fitness_center" to 5,
            "wb_sunny" to 0,
            "favorite" to 1,
            "star" to 2,
            "emoji_events" to 3,
        )
    return indices[key] ?: 0
}
