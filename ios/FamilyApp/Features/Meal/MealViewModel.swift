// Meal planning view model — the iOS twin of MealViewModel.kt. Same realtime/optimistic
// template as shopping.
import Foundation
import Observation
import Supabase

/// Per-plan meal progress: days with a dinner planned out of total days.
struct MealProgress: Equatable {
    let planned: Int
    let total: Int
}

@Observable
@MainActor
final class MealViewModel {
    private static var cache: [MealPlanModel] = []

    private(set) var plans: [MealPlanModel] = MealViewModel.cache
    private(set) var planProgress: [String: MealProgress] = [:]
    private(set) var isLoading = false
    private(set) var selectedPlan: MealPlanModel?
    private(set) var days: [MealPlanDayModel] = []

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private let plansObserver = RealtimeObserver()
    private var subscribedFamilyId: String?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await loadForCurrentFamily() }
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.loadForCurrentFamily()
            }
        }
    }

    isolated deinit {
        familyChangedTask?.cancel()
    }

    func refresh() {
        Task {
            guard let familyId = await currentFamilyId() else { return }
            await loadPlansOnly(familyId: familyId)
        }
    }

    private func currentFamilyId() async -> String? {
        guard let userId = repo.session.currentUserId else { return nil }
        return await repo.getUser(userId)?.familyId
    }

    private func loadForCurrentFamily() async {
        guard let familyId = await currentFamilyId() else {
            plans = []
            return
        }
        await loadPlansOnly(familyId: familyId)
        subscribeToPlansOnce(familyId: familyId)
    }

    /// Fetches meal plans without touching the realtime channel.
    private func loadPlansOnly(familyId: String) async {
        if plans.isEmpty { isLoading = true }
        if let result: [MealPlanModel] = try? await client.from("meal_plans")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value {
            Self.cache = result
            plans = result
            await loadPlanProgress(planIds: result.map(\.id))
        }
        isLoading = false
    }

    /// Loads planned/total day counts per plan in one query.
    private func loadPlanProgress(planIds: [String]) async {
        guard !planIds.isEmpty else {
            planProgress = [:]
            return
        }
        guard let allDays: [MealPlanDayModel] = try? await client.from("meal_plan_days")
            .select()
            .in("meal_plan_id", values: planIds)
            .execute()
            .value
        else { return }
        planProgress = Dictionary(grouping: allDays, by: \.mealPlanId).mapValues { days in
            MealProgress(
                planned: days.count { !$0.food.trimmingCharacters(in: .whitespaces).isEmpty },
                total: days.count
            )
        }
    }

    private func subscribeToPlansOnce(familyId: String) {
        guard subscribedFamilyId != familyId else { return }
        subscribedFamilyId = familyId
        plansObserver.start(
            table: "meal_plans",
            scope: familyId,
            filter: .eq("family_id", value: familyId)
        ) { [weak self] in
            await self?.loadPlansOnly(familyId: familyId)
        }
    }

    // MARK: - Detail

    func loadPlanDetail(_ planId: String) {
        Task { await reloadPlanDetail(planId) }
    }

    private func reloadPlanDetail(_ planId: String) async {
        async let planFetch: [MealPlanModel] = (try? client.from("meal_plans")
            .select()
            .eq("id", value: planId)
            .execute()
            .value) ?? []
        async let daysFetch: [MealPlanDayModel] = (try? client.from("meal_plan_days")
            .select()
            .eq("meal_plan_id", value: planId)
            .execute()
            .value) ?? []
        if let plan = await planFetch.first { selectedPlan = plan }
        days = await daysFetch.sorted { $0.date < $1.date }
    }

    // MARK: - Mutations

    func createPlan(name: String, fromIso: String, toIso: String, icon: String, color: Int?) {
        Task {
            guard let userId = repo.session.currentUserId,
                  let familyId = await repo.getUser(userId)?.familyId,
                  let from = LocalDate(iso: fromIso),
                  let to = LocalDate(iso: toIso)
            else { return }

            let week = isoWeekNumber(of: from)
            var temp = MealPlanModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.familyId = familyId
            temp.name = name
            temp.icon = icon
            temp.color = color
            temp.fromDate = fromIso
            temp.toDate = toIso
            temp.week = week
            plans.append(temp)

            do {
                let plan: MealPlanModel = try await client.from("meal_plans")
                    .insert([
                        "family_id": AnyJSON.string(familyId),
                        "name": .string(name),
                        "icon": .string(icon),
                        "from_date": .string(fromIso),
                        "to_date": .string(toIso),
                        "week": .integer(week),
                        "color": color.map { AnyJSON.integer($0) } ?? .null,
                    ])
                    .select()
                    .single()
                    .execute()
                    .value

                var current = from
                while !(to < current) {
                    try await client.from("meal_plan_days")
                        .insert([
                            "meal_plan_id": AnyJSON.string(plan.id),
                            "day": .string(current.fullDayName()),
                            "date": .string(current.isoString),
                        ])
                        .execute()
                    current = current.addingDays(1)
                }
            } catch {
                // Reload below drops the temp row on failure — no rollback logic (parity).
            }
            await loadPlansOnly(familyId: familyId)
        }
    }

    func renamePlan(_ plan: MealPlanModel, newName: String) {
        Task {
            var updated = plan
            updated.name = newName
            selectedPlan = updated
            plans = plans.map { $0.id == plan.id ? updated : $0 }
            Self.cache = plans
            _ = try? await client.from("meal_plans")
                .update(["name": AnyJSON.string(newName)])
                .eq("id", value: plan.id)
                .execute()
        }
    }

    func setPlanIcon(_ plan: MealPlanModel, newIcon: String) {
        Task {
            var updated = plan
            updated.icon = newIcon
            selectedPlan = updated
            plans = plans.map { $0.id == plan.id ? updated : $0 }
            Self.cache = plans
            _ = try? await client.from("meal_plans")
                .update(["icon": AnyJSON.string(newIcon)])
                .eq("id", value: plan.id)
                .execute()
        }
    }

    func setPlanColor(_ plan: MealPlanModel, color: Int?) {
        Task {
            var updated = plan
            updated.color = color
            selectedPlan = updated
            plans = plans.map { $0.id == plan.id ? updated : $0 }
            Self.cache = plans
            _ = try? await client.from("meal_plans")
                .update(["color": color.map { AnyJSON.integer($0) } ?? .null])
                .eq("id", value: plan.id)
                .execute()
        }
    }

    func deletePlan(_ plan: MealPlanModel) {
        Task {
            plans.removeAll { $0.id == plan.id }
            _ = try? await client.from("meal_plans").delete().eq("id", value: plan.id).execute()
            if let familyId = await currentFamilyId() {
                await loadPlansOnly(familyId: familyId)
            }
        }
    }

    func setFood(_ day: MealPlanDayModel, food: String) {
        Task {
            days = days.map { existing in
                var existing = existing
                if existing.id == day.id { existing.food = food }
                return existing
            }
            _ = try? await client.from("meal_plan_days")
                .update(["food": AnyJSON.string(food)])
                .eq("id", value: day.id)
                .execute()
            await reloadPlanDetail(day.mealPlanId)
        }
    }
}

// MARK: - Pure helpers (unit-tested)

/// ISO week number for a LocalDate — parity with Calendar.WEEK_OF_YEAR on Android.
func isoWeekNumber(of date: LocalDate) -> Int {
    var calendar = Calendar(identifier: .iso8601)
    guard let utc = TimeZone(identifier: "UTC") else {
        preconditionFailure("UTC time zone is always available")
    }
    calendar.timeZone = utc
    let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
    return calendar.component(.weekOfYear, from: instant)
}

/// English-stable default so unit tests keep asserting English; the UI passes `appLocale`.
private let defaultMealLocale = Locale(identifier: "en_US_POSIX")

/// "dd MMM" — mirrors MEAL_DATE_FMT ("05 Jul"); falls back to the raw string.
func formatMealDate(_ stored: String, locale: Locale = defaultMealLocale) -> String {
    guard let date = LocalDate(iso: stored) else { return stored }
    let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
    let formatter = DateFormatter()
    formatter.dateFormat = "dd MMM"
    formatter.locale = locale
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter.string(from: instant)
}

/// Plan card sub-label — mirrors the planLabel logic in MealScreens.kt.
func mealPlanLabel(
    progress: MealProgress?,
    fromIso: String,
    toIso: String,
    locale: Locale = defaultMealLocale
) -> String {
    if let progress, progress.total > 0 {
        return L("\(progress.planned) of \(progress.total) dinners planned", locale: locale)
    }
    guard let from = LocalDate(iso: fromIso), let to = LocalDate(iso: toIso) else {
        return L("\(0) days", locale: locale)
    }
    let count = max(from.daysUntil(to) + 1, 0)
    return L("\(count) days", locale: locale)
}

extension LocalDate {
    /// Full weekday name in the device locale — parity with Android's
    /// dayOfWeek.getDisplayName(FULL, Locale.getDefault()) used for meal_plan_days.day.
    func fullDayName(locale: Locale = .current) -> String {
        let instant = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE"
        formatter.locale = locale
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: instant)
    }
}
