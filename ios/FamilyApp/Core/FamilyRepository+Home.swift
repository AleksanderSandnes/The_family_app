// Home dashboard data access — moved out of HomeViewModel so the VM depends only on the
// FamilyRepositoryProtocol seam and can be unit-tested with a mock. Each method preserves
// the exact query semantics of the original inline `client.from(...)` calls and throws on
// failure so the caller can fall back to the existing value (`(try? …) ?? …`).
import Foundation
import Supabase

extension FamilyRepository {
    /// All meal plans for the family (the VM picks the one whose range covers today).
    func fetchMealPlans(familyId: String) async throws -> [MealPlanModel] {
        try await client.from("meal_plans")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value
    }

    /// Day rows for a plan on a specific ISO date (the VM reads today's food).
    func fetchMealPlanDays(mealPlanId: String, date: String) async throws -> [MealPlanDayModel] {
        try await client.from("meal_plan_days")
            .select()
            .eq("meal_plan_id", value: mealPlanId)
            .eq("date", value: date)
            .execute()
            .value
    }

    /// Calendar events scoped to the family (the VM selects the next upcoming one).
    func fetchFamilyCalendarEvents(familyId: String) async throws -> [CalendarEventModel] {
        try await client.from("calendar_events")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value
    }

    /// Birthdays scoped to the family (the VM applies the 7-day gate + soonest selection).
    func fetchFamilyBirthdays(familyId: String) async throws -> [BirthdayModel] {
        try await client.from("birthdays")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value
    }

    /// Shopping lists scoped to the family (the VM sums unchecked items across them).
    func fetchShoppingLists(familyId: String) async throws -> [ShoppingListModel] {
        try await client.from("shopping_lists")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value
    }

    /// Unchecked items across the given lists (empty when no lists — no query is issued).
    func fetchUncheckedShoppingItems(listIds: [String]) async throws -> [ShoppingItemModel] {
        guard !listIds.isEmpty else { return [] }
        return try await client.from("shopping_items")
            .select()
            .in("list_id", values: listIds)
            .eq("checked", value: false)
            .execute()
            .value
    }
}
