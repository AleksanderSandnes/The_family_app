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
        "work": "briefcase.fill",
        "school": "graduationcap.fill",
        "music_note": "music.note",
        "wb_sunny": "sun.max.fill",
        "emoji_events": "trophy.fill",
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

    static func calendarSymbol(_ key: String) -> String {
        symbol(key, fallback: "clock.fill")
    }

    static func wishlistSymbol(_ key: String) -> String {
        symbol(key, fallback: "gift.fill")
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

    static let calendar: [String] = [
        "schedule", "cake", "people", "work", "school", "restaurant", "flight",
        "local_hospital", "celebration", "shopping_cart", "music_note", "fitness_center",
        "wb_sunny", "favorite", "star", "emoji_events",
    ]

    static let wishlist: [String] = [
        "card_giftcard", "star", "favorite", "celebration", "flight", "home",
        "restaurant", "fitness_center", "shopping_cart", "pets", "local_hospital", "cake",
    ]
}

/// Icon key → stable index 0–5 used to color event dots/containers — mirrors
/// ICON_COLOR_INDEX in CalendarScreen.kt.
func calendarIconColorIndex(_ key: String) -> Int {
    let map: [String: Int] = [
        "schedule": 0, "cake": 1, "people": 2, "work": 3, "school": 4, "restaurant": 5,
        "flight": 0, "local_hospital": 1, "celebration": 2, "shopping_cart": 3,
        "music_note": 4, "fitness_center": 5, "wb_sunny": 0, "favorite": 1, "star": 2,
        "emoji_events": 3,
    ]
    return map[key] ?? 0
}
