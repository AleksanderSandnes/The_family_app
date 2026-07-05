// DB icon keys → SF Symbols. The Android app stores Material icon keys in the DB
// (shopping_lists.icon, meal_plans.icon, wishlists.icon, calendar_events.icon); both
// platforms must render the same key, so this map is the single source of truth on iOS.
import Foundation

enum IconKeyMap {
    /// Shared lookup across all features; falls back per feature via the helpers below.
    private static let map: [String: String] = [
        // Shopping list options
        "shopping_cart": "cart.fill",
        "local_hospital": "cross.case.fill",
        "celebration": "party.popper.fill",
        "favorite": "heart.fill",
        "star": "star.fill",
        "fitness_center": "dumbbell.fill",
        "home": "house.fill",
        "pets": "pawprint.fill",
        "flight": "airplane",
        "people": "person.2.fill",
        // Meal plan options
        "restaurant": "fork.knife",
        "restaurant_menu": "menucard.fill",
        "lunch_dining": "takeoutbag.and.cup.and.straw.fill",
        "dinner_dining": "fork.knife.circle.fill",
        "bakery_dining": "croissant.fill",
        "local_pizza": "triangle.lefthalf.filled",
        "ramen_dining": "cup.and.saucer.fill",
        "set_meal": "rectangle.grid.1x2.fill",
        "fastfood": "takeoutbag.and.cup.and.straw",
        "cake": "birthday.cake.fill",
        "local_cafe": "mug.fill",
        "outdoor_grill": "flame.fill",
        "kitchen": "refrigerator.fill",
        "egg": "oval.portrait.fill",
        "local_bar": "wineglass.fill",
        // Wishlist options
        "card_giftcard": "gift.fill",
        // Calendar options
        "schedule": "clock.fill",
    ]

    static func symbol(_ key: String, fallback: String) -> String {
        map[key] ?? fallback
    }

    static func shoppingSymbol(_ key: String) -> String {
        symbol(key, fallback: "cart.fill")
    }

    static func mealSymbol(_ key: String) -> String {
        symbol(key, fallback: "fork.knife")
    }
}

/// Icon-picker option lists — same keys and order as the Android dialogs.
enum IconOptions {
    static let shopping: [String] = [
        "shopping_cart", "restaurant", "cake", "local_hospital", "celebration", "favorite",
        "star", "fitness_center", "home", "pets", "flight", "people",
    ]

    static let meal: [String] = [
        "restaurant", "restaurant_menu", "lunch_dining", "dinner_dining", "bakery_dining",
        "local_pizza", "ramen_dining", "set_meal", "fastfood", "cake", "local_cafe",
        "outdoor_grill", "kitchen", "egg", "local_bar",
    ]
}
