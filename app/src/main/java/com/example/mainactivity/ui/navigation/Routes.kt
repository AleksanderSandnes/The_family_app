package com.example.mainactivity.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val CHAT = "chat"
    const val FAMILY = "family"
    const val PROFILE = "profile"

    const val SHOPPING = "shopping"
    const val SHOPPING_DETAIL = "shopping/{listId}"
    const val MEAL = "meal"
    const val MEAL_DETAIL = "meal/{planId}"
    const val BIRTHDAY = "birthday"
    const val WISHLIST = "wishlist"
    const val WISHLIST_DETAIL = "wishlist/{wishlistId}"
    const val CHAT_DETAIL = "chat/{conversationId}"
    const val PROFILE_EDIT = "profile/edit"
    const val SETTINGS = "settings"
    const val FAMILY_MAP = "family_map"

    fun shoppingDetail(id: String) = "shopping/$id"

    fun mealDetail(id: String) = "meal/$id"

    fun wishlistDetail(id: String) = "wishlist/$id"

    fun chatDetail(id: String) = "chat/$id"
}
