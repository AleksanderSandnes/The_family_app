// Calendar view model — the iOS twin of CalendarViewModel.kt.
import Foundation
import Observation
import Supabase

/// java.time.YearMonth equivalent for the month grid.
struct YearMonth: Hashable, Comparable {
    let year: Int
    let month: Int

    static func of(_ date: LocalDate) -> YearMonth {
        YearMonth(year: date.year, month: date.month)
    }

    static func now() -> YearMonth { .of(.today()) }

    var lengthOfMonth: Int { LocalDate.daysIn(month: month, year: year) }

    func atDay(_ day: Int) -> LocalDate { LocalDate(year: year, month: month, day: day) }

    func plusMonths(_ count: Int) -> YearMonth {
        let zeroBased = year * 12 + (month - 1) + count
        let newYear = zeroBased >= 0 ? zeroBased / 12 : (zeroBased - 11) / 12
        return YearMonth(year: newYear, month: zeroBased - newYear * 12 + 1)
    }

    /// "MMMM yyyy" English — mirrors MONTH_YEAR_FMT.
    var formatted: String {
        let months = ["January", "February", "March", "April", "May", "June", "July",
                      "August", "September", "October", "November", "December"]
        return "\(months[month - 1]) \(year)"
    }

    static func < (lhs: YearMonth, rhs: YearMonth) -> Bool {
        (lhs.year, lhs.month) < (rhs.year, rhs.month)
    }
}

/// The fields for a new calendar event, grouped into a single parameter.
struct EventDraft {
    var activity: String
    var allDay: Bool
    var dateFrom: String
    var dateTo: String
    var timeFrom: String
    var timeTo: String
    var icon = "schedule"
}

@Observable
@MainActor
final class CalendarViewModel {
    private static var cache: [CalendarEventModel] = []

    private(set) var selectedDate = LocalDate.today()
    private(set) var displayedMonth = YearMonth.now()
    private(set) var events: [CalendarEventModel] = CalendarViewModel.cache
    private(set) var isLoading = false

    var eventsForSelectedDate: [CalendarEventModel] {
        events.filter { event in
            guard let from = LocalDate(iso: event.dateFrom) else { return false }
            let to = LocalDate(iso: event.dateTo) ?? from
            return from <= selectedDate && selectedDate <= to
        }
    }

    private let repo = FamilyRepository.shared
    private var client: SupabaseClient { SupabaseClientProvider.client }
    private let observer = RealtimeObserver()
    private var subscribedFamilyId: String?
    private var familyChangedTask: Task<Void, Never>?

    init() {
        Task { await loadEvents() }
        familyChangedTask = Task { [weak self] in
            guard let stream = await self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.loadEvents()
            }
        }
    }

    deinit {
        familyChangedTask?.cancel()
    }

    func refresh() {
        Task { await loadEvents() }
    }

    private func loadEvents() async {
        guard let userId = repo.session.currentUserId else {
            events = []
            return
        }
        if events.isEmpty { isLoading = true }
        defer { isLoading = false }
        guard let user = await repo.getUser(userId) else { return }

        let result: [CalendarEventModel]
        if let familyId = user.familyId {
            let fetched: [CalendarEventModel] = (try? await client.from("calendar_events")
                .select()
                .or("user_id.eq.\(userId),family_id.eq.\(familyId)")
                .execute()
                .value) ?? events
            result = fetched.filter { $0.familyId == nil || $0.familyId == familyId }
        } else {
            let fetched: [CalendarEventModel] = (try? await client.from("calendar_events")
                .select()
                .eq("user_id", value: userId)
                .execute()
                .value) ?? events
            result = fetched.filter { $0.familyId == nil }
        }
        Self.cache = result
        events = result

        if let familyId = user.familyId, subscribedFamilyId != familyId {
            subscribedFamilyId = familyId
            observer.start(
                table: "calendar_events",
                scope: familyId,
                filter: "family_id=eq.\(familyId)"
            ) { [weak self] in
                await self?.loadEvents()
            }
        }
    }

    // MARK: - Selection

    func selectDate(_ date: LocalDate) {
        selectedDate = date
        displayedMonth = .of(date)
    }

    func nextMonth() { displayedMonth = displayedMonth.plusMonths(1) }

    func prevMonth() { displayedMonth = displayedMonth.plusMonths(-1) }

    // MARK: - Mutations

    func addEvent(_ draft: EventDraft) {
        Task {
            guard let userId = repo.session.currentUserId,
                  let user = await repo.getUser(userId) else { return }
            let resolvedDateTo = draft.dateTo.isEmpty ? draft.dateFrom : draft.dateTo

            var temp = CalendarEventModel()
            temp.id = "temp-\(UUID().uuidString)"
            temp.userId = userId
            temp.familyId = user.familyId
            temp.activity = draft.activity
            temp.allDay = draft.allDay
            temp.dateFrom = draft.dateFrom
            temp.dateTo = resolvedDateTo
            temp.timeFrom = draft.allDay ? "" : draft.timeFrom
            temp.timeTo = draft.allDay ? "" : draft.timeTo
            temp.icon = draft.icon
            events.append(temp)

            var payload: [String: AnyJSON] = [
                "user_id": .string(userId),
                "activity": .string(draft.activity),
                "all_day": .bool(draft.allDay),
                "date_from": .string(draft.dateFrom),
                "date_to": .string(resolvedDateTo),
                "time_from": .string(draft.allDay ? "" : draft.timeFrom),
                "time_to": .string(draft.allDay ? "" : draft.timeTo),
                "icon": .string(draft.icon),
            ]
            if let familyId = user.familyId { payload["family_id"] = .string(familyId) }
            try? await client.from("calendar_events").insert(payload).execute()
            await loadEvents()
        }
    }

    func updateEvent(_ event: CalendarEventModel) {
        Task {
            events = events.map { $0.id == event.id ? event : $0 }
            try? await client.from("calendar_events")
                .update([
                    "activity": AnyJSON.string(event.activity),
                    "all_day": .bool(event.allDay),
                    "date_from": .string(event.dateFrom),
                    "date_to": .string(event.dateTo),
                    "time_from": .string(event.timeFrom),
                    "time_to": .string(event.timeTo),
                    "icon": .string(event.icon),
                ])
                .eq("id", value: event.id)
                .execute()
            await loadEvents()
        }
    }

    func delete(_ event: CalendarEventModel) {
        Task {
            events.removeAll { $0.id == event.id }
            try? await client.from("calendar_events").delete().eq("id", value: event.id).execute()
            await loadEvents()
        }
    }
}

// MARK: - Pure helpers (unit-tested, mirror CalendarScreen.kt)

/// Month grid cells: leading nils to Monday-align, then each day, padded to full weeks.
func monthCells(_ month: YearMonth) -> [LocalDate?] {
    let first = month.atDay(1)
    // ISO weekday: Monday=1 … Sunday=7 → offset 0…6. epochDay 0 (1970-01-01) was a Thursday.
    let weekday = ((first.epochDay + 3) % 7 + 7) % 7 + 1
    var cells: [LocalDate?] = Array(repeating: nil, count: weekday - 1)
    for day in 1...month.lengthOfMonth {
        cells.append(month.atDay(day))
    }
    while cells.count % 7 != 0 { cells.append(nil) }
    return cells
}

/// Maps each date to the icon keys of events covering it (multi-day capped at 60 days).
func dateEventIcons(for events: [CalendarEventModel]) -> [LocalDate: [String]] {
    var map: [LocalDate: [String]] = [:]
    for event in events {
        guard let from = LocalDate(iso: event.dateFrom) else { continue }
        let to = LocalDate(iso: event.dateTo) ?? from
        var day = from
        while !(to < day) {
            map[day, default: []].append(event.icon)
            day = day.addingDays(1)
            if from.daysUntil(day) > 60 { break }
        }
    }
    return map
}

/// Concise time label for the event card — mirrors eventTimeLabel.
func eventTimeLabel(_ event: CalendarEventModel) -> String {
    if event.allDay { return "All day" }
    return [event.timeFrom, event.timeTo]
        .filter { !$0.isEmpty }
        .joined(separator: " – ")
}

/// "EEEE, d MMMM" English — mirrors SECTION_DATE_FMT.
func sectionDateLabel(_ date: LocalDate) -> String {
    let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86_400)
    let formatter = DateFormatter()
    formatter.dateFormat = "EEEE, d MMMM"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter.string(from: instant)
}
