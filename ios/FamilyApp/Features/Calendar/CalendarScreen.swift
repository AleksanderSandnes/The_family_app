// Calendar: Month/Week/Agenda toggle, Monday-first month grid with color-coded event
// dots, selected-day list, event sheet with icon picker, all-day toggle and date/time
// ranges.
import SwiftUI

private let weekdayLabels = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]

private enum CalendarViewMode: String, CaseIterable {
    case month = "Month"
    case week = "Week"
    case agenda = "Agenda"

    /// Localized segment title (localizes against the in-app language via the environment).
    var titleKey: LocalizedStringKey {
        switch self {
        case .month: "Month"
        case .week: "Week"
        case .agenda: "Agenda"
        }
    }
}

struct CalendarScreen: View {
    let viewModel: CalendarViewModel

    @State private var viewMode: CalendarViewMode = .month
    @State private var showAdd = false
    @State private var eventToEdit: CalendarEventModel?

    private var dotColorsByDate: [LocalDate: [Color]] {
        dateEventColors(for: viewModel.events)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(L("Calendar")) {
                HStack(spacing: Spacing.sm) {
                    Button("Today") { viewModel.selectDate(.today()) }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.appPrimary)
                        .padding(.horizontal, 14)
                        .frame(height: 36)
                        .glassCard(cornerRadius: 18)
                    Button { showAdd = true } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.appPrimary)
                            .frame(width: 36, height: 36)
                            .glassCard(cornerRadius: 18)
                    }
                    .accessibilityLabel("Add new calendar event")
                }
            }
            .padding(.horizontal, Spacing.screenEdge)

            Picker("View", selection: $viewMode) {
                ForEach(CalendarViewMode.allCases, id: \.self) { mode in
                    Text(mode.titleKey).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.vertical, 6)

            switch viewMode {
            case .month:
                MonthCalendarSection(
                    displayedMonth: viewModel.displayedMonth,
                    selectedDate: viewModel.selectedDate,
                    dotColorsByDate: dotColorsByDate,
                    onPrev: viewModel.prevMonth,
                    onNext: viewModel.nextMonth,
                    onDaySelected: viewModel.selectDate
                )
                .padding(.vertical, 10)
                .glassCard(cornerRadius: Radius.bigCard)
                .padding(.horizontal, Spacing.screenEdge)
                .padding(.top, 6)
                selectedDayList
            case .week:
                WeekStrip(
                    selectedDate: viewModel.selectedDate,
                    dotColorsByDate: dotColorsByDate,
                    onDaySelected: viewModel.selectDate
                )
                .padding(.vertical, 10)
                .glassCard(cornerRadius: Radius.bigCard)
                .padding(.horizontal, Spacing.screenEdge)
                .padding(.top, 6)
                selectedDayList
            case .agenda:
                AgendaList(
                    events: viewModel.events,
                    onEdit: { eventToEdit = $0 },
                    onDelete: { viewModel.delete($0) },
                    member: viewModel.member
                )
            }
        }
        .ambientBackground()
        .toolbar(.hidden, for: .navigationBar)
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            EventSheet(
                existingEvent: nil,
                initialDate: viewModel.selectedDate,
                members: viewModel.otherMembers
            ) { draft in
                viewModel.addEvent(draft)
                showAdd = false
            }
        }
        .sheet(item: $eventToEdit) { event in
            EventSheet(
                existingEvent: event,
                initialDate: viewModel.selectedDate,
                members: viewModel.otherMembers
            ) { draft in
                var updated = event
                updated.activity = draft.activity
                updated.allDay = draft.allDay
                updated.dateFrom = draft.dateFrom
                updated.dateTo = draft.dateTo
                updated.timeFrom = draft.timeFrom
                updated.timeTo = draft.timeTo
                updated.icon = draft.icon
                updated.isPrivate = draft.isPrivate
                updated.color = draft.color
                updated.attendeeIds = draft.attendeeIds
                viewModel.updateEvent(updated)
                eventToEdit = nil
            }
        }
    }

    @ViewBuilder private var selectedDayList: some View {
        Text(sectionDateLabel(viewModel.selectedDate, locale: appLocale).uppercased())
            .font(.sectionLabel)
            .tracking(0.6)
            .foregroundStyle(Color.appCaption)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.screenEdge + 6)
            .padding(.top, 16)
            .padding(.bottom, 8)

        let dayEvents = viewModel.eventsForSelectedDate
        if viewModel.isLoading {
            LoadingState().frame(maxHeight: .infinity, alignment: .top)
        } else if dayEvents.isEmpty {
            EmptyState(systemImage: "calendar", title: L("No events"), subtitle: L("Tap + to add an event."))
                .frame(maxHeight: .infinity)
        } else {
            List {
                ForEach(dayEvents) { event in
                    EventCard(event: event, member: viewModel.member) { eventToEdit = event }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(
                            top: 5, leading: Spacing.screenEdge, bottom: 5, trailing: Spacing.screenEdge
                        ))
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                viewModel.delete(event)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            // Breathing room so the first card's glass glow doesn't streak against the
            // date label above it (a blue line in light mode).
            .contentMargins(.top, Spacing.sm, for: .scrollContent)
        }
    }
}

// MARK: - Month grid

private struct MonthCalendarSection: View {
    let displayedMonth: YearMonth
    let selectedDate: LocalDate
    let dotColorsByDate: [LocalDate: [Color]]
    let onPrev: () -> Void
    let onNext: () -> Void
    let onDaySelected: (LocalDate) -> Void

    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Button(action: onPrev) {
                    Image(systemName: "chevron.left")
                        .accessibilityLabel("Previous month")
                }
                Spacer()
                Text(displayedMonth.formatted(locale: appLocale))
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Spacer()
                Button(action: onNext) {
                    Image(systemName: "chevron.right")
                        .accessibilityLabel("Next month")
                }
            }
            .padding(.horizontal, Spacing.md)

            WeekdayHeader()

            let cells = monthCells(displayedMonth)
            let today = LocalDate.today()
            ForEach(0..<(cells.count / 7), id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { column in
                        let date = cells[row * 7 + column]
                        DayCell(
                            date: date,
                            isSelected: date == selectedDate,
                            isToday: date == today,
                            eventDotColors: date.flatMap { dotColorsByDate[$0] } ?? []
                        ) {
                            if let date { onDaySelected(date) }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .animation(.easeInOut(duration: Motion.fade), value: displayedMonth)
    }
}

private struct WeekdayHeader: View {
    var body: some View {
        HStack(spacing: 0) {
            ForEach(weekdayLabels, id: \.self) { label in
                Text(LocalizedStringKey(label))
                    .font(.labelMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 2)
    }
}

private struct WeekStrip: View {
    let selectedDate: LocalDate
    let dotColorsByDate: [LocalDate: [Color]]
    let onDaySelected: (LocalDate) -> Void

    var body: some View {
        VStack(spacing: 2) {
            WeekdayHeader()
            let today = LocalDate.today()
            // Monday of the selected week (epochDay 0 was a Thursday).
            let weekday = ((selectedDate.epochDay + 3) % 7 + 7) % 7 + 1
            let monday = selectedDate.addingDays(-(weekday - 1))
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { offset in
                    let date = monday.addingDays(offset)
                    DayCell(
                        date: date,
                        isSelected: date == selectedDate,
                        isToday: date == today,
                        eventDotColors: dotColorsByDate[date] ?? []
                    ) { onDaySelected(date) }
                }
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
    }
}

private struct DayCell: View {
    let date: LocalDate?
    let isSelected: Bool
    let isToday: Bool
    let eventDotColors: [Color]
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                if let date {
                    ZStack {
                        Circle()
                            .fill(backgroundColor)
                            .frame(width: 34, height: 34)
                        if isSelected, !isToday {
                            Circle()
                                .strokeBorder(Color.appPrimary, lineWidth: 1.5)
                                .frame(width: 34, height: 34)
                        }
                        Text("\(date.day)")
                            .font(.bodyMedium.weight(isToday || isSelected ? .bold : .regular))
                            .foregroundStyle(textColor)
                    }
                    HStack(spacing: 2) {
                        if eventDotColors.isEmpty {
                            // Invisible placeholder keeps every cell the same height.
                            Color.clear.frame(width: 6, height: 6)
                        } else {
                            ForEach(Array(eventDotColors.prefix(3).enumerated()), id: \.offset) { _, dotColor in
                                Circle()
                                    .fill(dotColor)
                                    .frame(width: 6, height: 6)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, minHeight: 48)
        }
        .buttonStyle(.plain)
        .disabled(date == nil)
        .accessibilityLabel(accessibilityText)
    }

    private var backgroundColor: Color {
        if isToday { return .appPrimary }
        if isSelected { return .appPrimaryContainer }
        return .clear
    }

    private var textColor: Color {
        if isToday { return .appOnPrimary }
        if isSelected { return .appOnPrimaryContainer }
        return .appOnSurface
    }

    private var accessibilityText: String {
        guard let date else { return "" }
        let count = eventDotColors.count
        return "\(sectionDateLabel(date, locale: appLocale)). \(count == 1 ? L("1 event") : L("\(count) events"))"
    }
}

// MARK: - Agenda

private struct AgendaList: View {
    let events: [CalendarEventModel]
    let onEdit: (CalendarEventModel) -> Void
    let onDelete: (CalendarEventModel) -> Void
    var member: (String) -> UserModel? = { _ in nil }

    var body: some View {
        let today = LocalDate.today()
        let upcoming = events
            .filter { event in
                let endIso = event.dateTo.isEmpty ? event.dateFrom : event.dateTo
                guard let end = LocalDate(iso: endIso) else { return false }
                return end >= today
            }
            .sorted { lhs, rhs in
                let lhsDate = LocalDate(iso: lhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                let rhsDate = LocalDate(iso: rhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                return lhsDate < rhsDate
            }
        let grouped = Dictionary(grouping: upcoming) { LocalDate(iso: $0.dateFrom) }
        let orderedDates = grouped.keys.sorted {
            ($0 ?? LocalDate(year: 9999, month: 12, day: 31)) < ($1 ?? LocalDate(year: 9999, month: 12, day: 31))
        }

        if upcoming.isEmpty {
            EmptyState(
                systemImage: "calendar",
                title: L("Nothing coming up"),
                subtitle: L("Tap + to add an event.")
            )
            .frame(maxHeight: .infinity)
        } else {
            List {
                ForEach(Array(orderedDates.enumerated()), id: \.offset) { _, date in
                    Section {
                        ForEach(grouped[date] ?? []) { event in
                            EventCard(event: event, member: member) { onEdit(event) }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(
                                    top: 4, leading: Spacing.screenEdge,
                                    bottom: 4, trailing: Spacing.screenEdge
                                ))
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        onDelete(event)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                        }
                    } header: {
                        Text(date.map { sectionDateLabel($0, locale: appLocale) } ?? L("Upcoming"))
                            .font(.labelLarge.weight(.semibold))
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            // Breathing room so the first card's glass glow doesn't streak against the
            // date label above it (a blue line in light mode).
            .contentMargins(.top, Spacing.sm, for: .scrollContent)
        }
    }
}

// MARK: - Event card

private struct EventCard: View {
    let event: CalendarEventModel
    var member: (String) -> UserModel? = { _ in nil }
    let onEdit: () -> Void

    var body: some View {
        let accent = calendarEventColor(event)
        let timeLabel = eventTimeLabel(event, locale: appLocale)
        let people = ([event.userId] + event.attendeeIds).compactMap(member)
        Button(action: onEdit) {
            HStack(spacing: Spacing.md) {
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(accent.opacity(0.16))
                    .frame(width: 42, height: 42)
                    .overlay(
                        Image(systemName: IconKeyMap.calendarSymbol(event.icon))
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(accent)
                    )
                VStack(alignment: .leading, spacing: 2) {
                    Text(event.activity)
                        .font(.cardTitle)
                        .foregroundStyle(Color.appOnSurface)
                    if !timeLabel.isEmpty {
                        Text(timeLabel)
                            .font(.caption)
                            .foregroundStyle(Color.appCaption)
                    }
                    if !people.isEmpty {
                        peopleView(people)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, 12)
            .glassCard(cornerRadius: Radius.row)
        }
        .buttonStyle(.plain)
    }

    /// Names when there are few; overlapping avatars when they'd overflow the row.
    @ViewBuilder
    private func peopleView(_ people: [UserModel]) -> some View {
        if people.count <= 2 {
            Text(people.map(\.name).joined(separator: ", "))
                .font(.caption)
                .foregroundStyle(Color.appCaption)
                .lineLimit(1)
        } else {
            HStack(spacing: -6) {
                ForEach(people.prefix(6)) { person in
                    InitialAvatar(user: person, size: 22)
                        .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 1.5))
                }
            }
        }
    }
}

// MARK: - Event colour

/// The curated, on-brand palette offered in the event colour picker (0xRRGGBB).
let calendarEventColorPalette: [Int] = [
    0x6366F1, // indigo
    0x8B5CF6, // violet
    0xEC4899, // pink
    0x3B82F6, // blue
    0x14B8A6, // teal
    0x22C55E, // green
    0xF59E0B, // amber
    0xEF4444, // red
]

/// Adaptive feature-derived dot colours used when an event has no custom colour.
private let calendarDotFallback: [Color] = [
    FeatureAccent.calendar.dot, FeatureAccent.shopping.dot, FeatureAccent.birthdays.dot,
    FeatureAccent.meals.dot, FeatureAccent.wishlists.dot, FeatureAccent.map.dot,
]

/// An event's display colour — the user-picked colour, else an icon-derived accent.
func calendarEventColor(_ event: CalendarEventModel) -> Color {
    if let hex = event.color { return Color(hex: UInt32(truncatingIfNeeded: hex)) }
    return calendarDotFallback[calendarIconColorIndex(event.icon) % calendarDotFallback.count]
}

/// Maps each date to the display colours of events covering it (multi-day capped at 60 days).
func dateEventColors(for events: [CalendarEventModel]) -> [LocalDate: [Color]] {
    var map: [LocalDate: [Color]] = [:]
    for event in events {
        guard let from = LocalDate(iso: event.dateFrom) else { continue }
        let to = LocalDate(iso: event.dateTo) ?? from
        let color = calendarEventColor(event)
        var day = from
        while !(to < day) {
            map[day, default: []].append(color)
            day = day.addingDays(1)
            if from.daysUntil(day) > 60 { break }
        }
    }
    return map
}
