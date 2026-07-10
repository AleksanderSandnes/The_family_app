// Meal-planning data access behind the FamilyRepositoryProtocol seam, so the view model is
// unit-testable with a mock. Fetches throw so the caller can fall back to its current value
// (`(try? …) ?? …`).
//
// Note: `fetchMealPlans(familyId:)` and `fetchMealPlanDays(mealPlanId:date:)` live in
// FamilyRepository+Home.swift; the meal list reuses the former.
import Foundation
import Supabase

extension FamilyRepository {
    /// Day rows across several plans in one query — feeds the per-plan progress counts.
    func fetchMealPlanDays(mealPlanIds: [String]) async throws -> [MealPlanDayModel] {
        guard !mealPlanIds.isEmpty else { return [] }
        return try await client.from("meal_plan_days")
            .select()
            .in("meal_plan_id", values: mealPlanIds)
            .execute()
            .value
    }

    /// A single plan by id (detail screen picks `.first`).
    func fetchMealPlans(planId: String) async throws -> [MealPlanModel] {
        try await client.from("meal_plans")
            .select()
            .eq("id", value: planId)
            .execute()
            .value
    }

    /// All day rows for one plan (detail screen; no date filter).
    func fetchMealPlanDays(mealPlanId: String) async throws -> [MealPlanDayModel] {
        try await client.from("meal_plan_days")
            .select()
            .eq("meal_plan_id", value: mealPlanId)
            .execute()
            .value
    }

    /// Inserts a plan and returns the persisted row (with its real id) so day rows can be
    /// attached. Throws so the caller skips day creation on failure (no rollback).
    func insertMealPlan(_ plan: MealPlanModel) async throws -> MealPlanModel {
        try await client.from("meal_plans")
            .insert([
                "family_id": AnyJSON.string(plan.familyId),
                "name": .string(plan.name),
                "icon": .string(plan.icon),
                "from_date": .string(plan.fromDate),
                "to_date": .string(plan.toDate),
                "week": .integer(plan.week),
                "color": plan.color.map { AnyJSON.integer($0) } ?? .null,
            ])
            .select()
            .single()
            .execute()
            .value
    }

    /// Inserts one day row for a plan. Throws so the caller's loop can handle failure.
    func insertMealPlanDay(mealPlanId: String, day: String, date: String) async throws {
        try await client.from("meal_plan_days")
            .insert([
                "meal_plan_id": AnyJSON.string(mealPlanId),
                "day": .string(day),
                "date": .string(date),
            ])
            .execute()
    }

    func renameMealPlan(id: String, name: String) async {
        _ = try? await client.from("meal_plans")
            .update(["name": AnyJSON.string(name)])
            .eq("id", value: id)
            .execute()
    }

    func setMealPlanIcon(id: String, icon: String) async {
        _ = try? await client.from("meal_plans")
            .update(["icon": AnyJSON.string(icon)])
            .eq("id", value: id)
            .execute()
    }

    func setMealPlanColor(id: String, color: Int?) async {
        _ = try? await client.from("meal_plans")
            .update(["color": color.map { AnyJSON.integer($0) } ?? .null])
            .eq("id", value: id)
            .execute()
    }

    func deleteMealPlan(id: String) async {
        _ = try? await client.from("meal_plans").delete().eq("id", value: id).execute()
    }

    func setMealDayFood(id: String, food: String) async {
        _ = try? await client.from("meal_plan_days")
            .update(["food": AnyJSON.string(food)])
            .eq("id", value: id)
            .execute()
    }
}
