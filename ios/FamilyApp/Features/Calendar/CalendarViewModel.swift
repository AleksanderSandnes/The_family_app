// Calendar view model.
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

    static func now() -> YearMonth {
        .of(.today())
    }

    var lengthOfMonth: Int {
        LocalDate.daysIn(month: month, year: year)
    }

    func atDay(_ day: Int) -> LocalDate {
        LocalDate(year: year, month: month, day: day)
    }

    func plusMonths(_ count: Int) -> YearMonth {
        let zeroBased = year * 12 + (month - 1) + count
        // Floor toward -inf so month arithmetic wraps correctly for dates before year 0.
        let newYear = zeroBased >= 0 ? zeroBased / 12 : (zeroBased - 11) / 12
        return YearMonth(year: newYear, month: zeroBased - newYear * 12 + 1)
    }

    /// "MMMM yyyy" in the in-app language.
    func formatted(locale: Locale = Locale(identifier: "en_US_POSIX")) -> String {
        let instant = Date(timeIntervalSince1970: TimeInterval(atDay(1).epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateFormat = "LLLL yyyy"
        formatter.locale = locale
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: instant)
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
    var isPrivate = false
    var color: Int?
    var attendeeIds: [String] = []
}

@Observable
@MainActor
final class CalendarViewModel {
    private static var cache: [CalendarEventModel] = []

    private(set) var selectedDate = LocalDate.today()
    private(set) var displayedMonth = YearMonth.now()
    private(set) var events: [CalendarEventModel] = CalendarViewModel.cache
    private(set) var familyMembers: [UserModel] = []
    private(set) var isLoading = false
    /// Whether the current user is the family admin — drives creator-or-admin delete gating.
    private(set) var isAdmin = false

    var currentUserId: String? {
        repo.session.currentUserId
    }

    /// Family members other than me — the selectable "Going with" list.
    var otherMembers: [UserModel] {
        familyMembers.filter { $0.id != currentUserId }
    }

    /// The member for an id (event creator / attendee); nil if unknown.
    func member(_ id: String) -> UserModel? {
        familyMembers.first { $0.id == id }
    }

    var eventsForSelectedDate: [CalendarEventModel] {
        events.filter { event in
            guard let from = LocalDate(iso: event.dateFrom) else { return false }
            let to = LocalDate(iso: event.dateTo) ?? from
            return from <= selectedDate && selectedDate <= to
        }
    }

    private let repo: FamilyRepositoryProtocol
    private let observer: RealtimeObserving
    private var subscribedFamilyId: String?
    private var familyChangedTask: Task<Void, Never>?

    init(
        repo: FamilyRepositoryProtocol? = nil,
        realtime: @MainActor () -> RealtimeObserving = { RealtimeObserver() }
    ) {
        self.repo = repo ?? FamilyRepository.shared
        observer = realtime()
        Task { await loadEvents() }
        familyChangedTask = Task { [weak self] in
            guard let stream = self?.repo.familyChanged() else { return }
            for await _ in stream {
                await self?.loadEvents()
            }
        }
    }

    isolated deinit {
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
        isAdmin = await repo.isFamilyAdmin(userId: userId)
        guard let user = await repo.getUser(userId) else { return }
        if let familyId = user.familyId {
            familyMembers = await repo.getFamilyMembers(familyId: familyId)
        }

        // Keep the current list on fetch failure rather than clearing it.
        let result = await (try? repo.fetchCalendarEvents(userId: userId, familyId: user.familyId)) ?? events
        Self.cache = result
        events = result

        if let familyId = user.familyId, subscribedFamilyId != familyId {
            subscribedFamilyId = familyId
            observer.start(
                table: "calendar_events",
                scope: familyId,
                filter: .eq("family_id", value: familyId)
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

    func nextMonth() {
        displayedMonth = displayedMonth.plusMonths(1)
    }

    func prevMonth() {
        displayedMonth = displayedMonth.plusMonths(-1)
    }

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
            temp.isPrivate = draft.isPrivate
            temp.color = draft.color
            temp.attendeeIds = draft.attendeeIds
            events.append(temp)

            await repo.insertCalendarEvent(temp)
            await loadEvents()
        }
    }

    func updateEvent(_ event: CalendarEventModel) {
        Task {
            events = events.map { $0.id == event.id ? event : $0 }
            await repo.updateCalendarEvent(event)
            await loadEvents()
        }
    }

    func delete(_ event: CalendarEventModel) {
        Task {
            events.removeAll { $0.id == event.id }
            await repo.deleteCalendarEvent(id: event.id)
            await loadEvents()
        }
    }
}

// MARK: - Pure helpers (unit-tested)

/// Month grid cells: leading nils to Monday-align, then each day, padded to full weeks.
func monthCells(_ month: YearMonth) -> [LocalDate?] {
    let first = month.atDay(1)
    // ISO weekday: Monday=1 … Sunday=7 → offset 0…6. epochDay 0 (1970-01-01) was a Thursday.
    let weekday = ((first.epochDay + 3) % 7 + 7) % 7 + 1
    var cells: [LocalDate?] = Array(repeating: nil, count: weekday - 1)
    for day in 1...month.lengthOfMonth {
        cells.append(month.atDay(day))
    }
    while !cells.count.isMultiple(of: 7) {
        cells.append(nil)
    }
    return cells
}

/// Concise time label for the event card.
func eventTimeLabel(
    _ event: CalendarEventModel,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    if event.allDay { return L("All day", locale: locale) }
    return [event.timeFrom, event.timeTo]
        .filter { !$0.isEmpty }
        .joined(separator: " – ")
}

/// "EEEE, d MMMM" in the in-app language.
func sectionDateLabel(
    _ date: LocalDate,
    locale: Locale = Locale(identifier: "en_US_POSIX")
) -> String {
    let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
    let formatter = DateFormatter()
    formatter.dateFormat = "EEEE, d MMMM"
    formatter.locale = locale
    formatter.timeZone = TimeZone(identifier: "UTC")
    return formatter.string(from: instant)
}
