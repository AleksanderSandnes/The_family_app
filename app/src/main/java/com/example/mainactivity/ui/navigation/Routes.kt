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

    fun shoppingDetail(id: Long) = "shopping/$id"
    fun mealDetail(id: Long) = "meal/$id"
    fun wishlistDetail(id: Long) = "wishlist/$id"
    fun chatDetail(id: Long) = "chat/$id"
}
