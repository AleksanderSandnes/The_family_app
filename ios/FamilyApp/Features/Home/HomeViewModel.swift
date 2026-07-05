// Home dashboard view model — the iOS twin of HomeViewModel.kt: user + family header
// data plus a best-effort glanceable summary (tonight's meal, next event, next
// birthday, shopping remaining).
import Foundation
import Observation
import Supabase

struct HomeUiState {
    var user: UserModel?
    var family: FamilyModel?
    var memberCount = 0
    var isLoading = true
    var loadError = false
    var tonightMeal: String?
    var nextEventTitle: String?
    var nextEventWhen: String?
    var nextBirthdayName: String?
    var nextBirthdayWhen: String?
    var shoppingRemaining = 0

    var hasSummary: Bool {
        tonightMeal != nil || nextEventTitle != nil || nextBirthdayName != nil
            || shoppingRemaining > 0
    }
}

private struct HomeSummary {
    var tonightMeal: String?
    var nextEventTitle: String?
    var nextEventWhen: String?
    var nextBirthdayName: String?
    var nextBirthdayWhen: String?
    var shoppingRemaining = 0
}

@Observable
@MainActor
final class HomeViewModel {
    var state = HomeUiState()

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient {
        SupabaseClientProvider.client
    }

    private var familyChangedTask: Task<Void, Never>?

    init() {
        refresh()
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
            for await _ in stream {
                self?.refresh()
            }
        }
    }

    isolated deinit {
        familyChangedTask?.cancel()
    }

    func refresh() {
        Task { await load() }
    }

    private func load() async {
        guard let userId = repo.session.currentUserId else {
            state = HomeUiState(isLoading: false)
            return
        }
        state.isLoading = true
        state.loadError = false

        guard let user = await repo.getUser(userId) else {
            state = HomeUiState(isLoading: false, loadError: true)
            return
        }
        var family: FamilyModel?
        var memberCount = 0
        var summary = HomeSummary()
        if let familyId = user.familyId {
            family = await repo.getFamily(familyId: familyId)
            memberCount = await repo.getFamilyMembers(familyId: familyId).count
            // Summary is best-effort — a failure here must not blank the whole screen.
            summary = await (try? loadSummary(familyId: familyId)) ?? HomeSummary()
        }
        state = HomeUiState(
            user: user,
            family: family,
            memberCount: memberCount,
            isLoading: false,
            tonightMeal: summary.tonightMeal,
            nextEventTitle: summary.nextEventTitle,
            nextEventWhen: summary.nextEventWhen,
            nextBirthdayName: summary.nextBirthdayName,
            nextBirthdayWhen: summary.nextBirthdayWhen,
            shoppingRemaining: summary.shoppingRemaining
        )
    }

    private func loadSummary(familyId: String) async throws -> HomeSummary {
        let today = LocalDate.today()
        let event = await loadNextEvent(familyId: familyId, today: today)
        let birthday = await loadNextBirthday(familyId: familyId, today: today)
        return await HomeSummary(
            tonightMeal: loadTonightMeal(familyId: familyId, today: today),
            nextEventTitle: event?.activity,
            nextEventWhen: event.map { eventWhen($0, today: today, locale: appLocale) },
            nextBirthdayName: birthday?.model.name,
            nextBirthdayWhen: birthday.map { birthdayWhen($0.model, next: $0.next, today: today, locale: appLocale) },
            shoppingRemaining: loadShoppingRemaining(familyId: familyId)
        )
    }

    /// Tonight's meal: the plan whose range covers today, then today's day row.
    private func loadTonightMeal(familyId: String, today: LocalDate) async -> String? {
        let plans: [MealPlanModel] = await (try? client.from("meal_plans")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
        let active = plans.first { plan in
            guard let from = LocalDate(iso: plan.fromDate),
                  let to = LocalDate(iso: plan.toDate) else { return false }
            return from <= today && today <= to
        }
        guard let active else { return nil }
        let days: [MealPlanDayModel] = await (try? client.from("meal_plan_days")
            .select()
            .eq("meal_plan_id", value: active.id)
            .eq("date", value: today.isoString)
            .execute()
            .value) ?? []
        let food = days.first?.food.trimmingCharacters(in: .whitespaces) ?? ""
        return food.isEmpty ? nil : food
    }

    /// Next upcoming event (ends today or later).
    private func loadNextEvent(familyId: String, today: LocalDate) async -> CalendarEventModel? {
        let events: [CalendarEventModel] = await (try? client.from("calendar_events")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
        return events
            .filter { event in
                let endIso = event.dateTo.isEmpty ? event.dateFrom : event.dateTo
                guard let end = LocalDate(iso: endIso) else { return false }
                return end >= today
            }
            .min { lhs, rhs in
                let lhsDate = LocalDate(iso: lhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                let rhsDate = LocalDate(iso: rhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                return lhsDate < rhsDate
            }
    }

    /// Soonest upcoming birthday paired with its next occurrence date.
    private func loadNextBirthday(
        familyId: String,
        today: LocalDate
    ) async -> (model: BirthdayModel, next: LocalDate)? {
        let birthdays: [BirthdayModel] = await (try? client.from("birthdays")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
        return birthdays
            .compactMap { birthday in
                nextBirthdayDate(birthday.date, today: today).map { (model: birthday, next: $0) }
            }
            .min { $0.next < $1.next }
    }

    /// Items left to buy across all family lists.
    private func loadShoppingRemaining(familyId: String) async -> Int {
        let lists: [ShoppingListModel] = await (try? client.from("shopping_lists")
            .select()
            .eq("family_id", value: familyId)
            .execute()
            .value) ?? []
        let ids = lists.map(\.id)
        guard !ids.isEmpty else { return 0 }
        let items: [ShoppingItemModel] = await (try? client.from("shopping_items")
            .select()
            .in("list_id", values: ids)
            .eq("checked", value: false)
            .execute()
            .value) ?? []
        return items.count
    }
}

// MARK: - Pure formatting helpers (unit-tested, mirror HomeViewModel.kt)

func eventWhen(
    _ event: CalendarEventModel,
    today: LocalDate,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    guard let from = LocalDate(iso: event.dateFrom) else { return event.dateFrom }
    let datePart: String = if from == today {
        L("Today", locale: locale)
    } else if from == today.addingDays(1) {
        L("Tomorrow", locale: locale)
    } else {
        from.formattedShort(locale: locale)
    }
    return (event.allDay || event.timeFrom.isEmpty) ? datePart : "\(datePart) · \(event.timeFrom)"
}

func birthdayWhen(
    _ birthday: BirthdayModel,
    next: LocalDate,
    today: LocalDate,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    let days = today.daysUntil(next)
    let whenText = switch days {
    case 0: L("Today!", locale: locale)
    case 1: L("Tomorrow", locale: locale)
    default: L("in \(days) days", locale: locale)
    }
    if let age = turnsAge(birthday.date, today: today) {
        return "\(L("Turns \(age)", locale: locale)) · \(whenText)"
    }
    return whenText
}
