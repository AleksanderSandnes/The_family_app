// Calendar — the iOS twin of CalendarScreen.kt: Month/Week/Agenda toggle, custom
// Monday-first month grid with color-coded event dots, selected-day list, event sheet
// with icon picker, all-day toggle and date/time ranges.
import SwiftUI

private let weekdayLabels = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"]

private enum CalendarViewMode: String, CaseIterable {
    case month = "Month"
    case week = "Week"
    case agenda = "Agenda"
}

struct CalendarScreen: View {
    let viewModel: CalendarViewModel

    @State private var viewMode: CalendarViewMode = .month
    @State private var showAdd = false
    @State private var eventToEdit: CalendarEventModel?

    private var iconsByDate: [LocalDate: [String]] {
        dateEventIcons(for: viewModel.events)
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("View", selection: $viewMode) {
                ForEach(CalendarViewMode.allCases, id: \.self) { mode in
                    Text(mode.rawValue).tag(mode)
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
                    iconsByDate: iconsByDate,
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
                    iconsByDate: iconsByDate,
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
                    onDelete: { viewModel.delete($0) }
                )
            }
        }
        .ambientBackground()
        .navigationTitle("Calendar")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button("Today") { viewModel.selectDate(.today()) }
                Button {
                    showAdd = true
                } label: {
                    Image(systemName: "plus")
                        .accessibilityLabel("Add new calendar event")
                }
            }
        }
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            EventSheet(existingEvent: nil, initialDate: viewModel.selectedDate) { draft in
                viewModel.addEvent(draft)
                showAdd = false
            }
        }
        .sheet(item: $eventToEdit) { event in
            EventSheet(existingEvent: event, initialDate: viewModel.selectedDate) { draft in
                var updated = event
                updated.activity = draft.activity
                updated.allDay = draft.allDay
                updated.dateFrom = draft.dateFrom
                updated.dateTo = draft.dateTo
                updated.timeFrom = draft.timeFrom
                updated.timeTo = draft.timeTo
                updated.icon = draft.icon
                viewModel.updateEvent(updated)
                eventToEdit = nil
            }
        }
    }

    @ViewBuilder private var selectedDayList: some View {
        Text(sectionDateLabel(viewModel.selectedDate).uppercased())
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
            EmptyState(systemImage: "calendar", title: "No events", subtitle: "Tap + to add an event.")
                .frame(maxHeight: .infinity)
        } else {
            List {
                ForEach(dayEvents) { event in
                    EventCard(event: event) { eventToEdit = event }
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
        }
    }
}

// MARK: - Month grid

private struct MonthCalendarSection: View {
    let displayedMonth: YearMonth
    let selectedDate: LocalDate
    let iconsByDate: [LocalDate: [String]]
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
                Text(displayedMonth.formatted)
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
                            eventIcons: date.flatMap { iconsByDate[$0] } ?? []
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
                Text(label)
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
    let iconsByDate: [LocalDate: [String]]
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
                        eventIcons: iconsByDate[date] ?? []
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
    let eventIcons: [String]
    let onTap: () -> Void

    /// Feature-coloured event dots (spec 2b) — a day may stack up to 3.
    private let dotColors: [Color] = [
        FeatureAccent.calendar.dot, FeatureAccent.shopping.dot, FeatureAccent.birthdays.dot,
        FeatureAccent.meals.dot, FeatureAccent.wishlists.dot, FeatureAccent.map.dot,
    ]

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
                        if eventIcons.isEmpty {
                            Color.clear.frame(width: 6, height: 6)
                        } else {
                            ForEach(Array(eventIcons.prefix(3).enumerated()), id: \.offset) { _, icon in
                                Circle()
                                    .fill(dotColors[calendarIconColorIndex(icon) % dotColors.count])
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
        let count = eventIcons.count
        return "\(sectionDateLabel(date)). \(count) \(count == 1 ? "event" : "events")"
    }
}

// MARK: - Agenda

private struct AgendaList: View {
    let events: [CalendarEventModel]
    let onEdit: (CalendarEventModel) -> Void
    let onDelete: (CalendarEventModel) -> Void

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
                title: "Nothing coming up",
                subtitle: "Tap + to add an event."
            )
            .frame(maxHeight: .infinity)
        } else {
            List {
                ForEach(Array(orderedDates.enumerated()), id: \.offset) { _, date in
                    Section {
                        ForEach(grouped[date] ?? []) { event in
                            EventCard(event: event) { onEdit(event) }
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
                        Text(date.map(sectionDateLabel) ?? "Upcoming")
                            .font(.labelLarge.weight(.semibold))
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }
}

// MARK: - Event card

private struct EventCard: View {
    let event: CalendarEventModel
    let onEdit: () -> Void

    private let features: [FeatureAccent] = [.calendar, .shopping, .birthdays, .meals, .wishlists, .map]

    var body: some View {
        let feature = features[calendarIconColorIndex(event.icon) % features.count]
        let timeLabel = eventTimeLabel(event)
        Button(action: onEdit) {
            HStack(spacing: Spacing.md) {
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .fill(feature.badgeFill)
                    .frame(width: 42, height: 42)
                    .overlay(
                        Image(systemName: IconKeyMap.calendarSymbol(event.icon))
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(feature.stroke)
                    )
                VStack(alignment: .leading, spacing: 1) {
                    Text(event.activity)
                        .font(.cardTitle)
                        .foregroundStyle(Color.appOnSurface)
                    if !timeLabel.isEmpty {
                        Text(timeLabel)
                            .font(.caption)
                            .foregroundStyle(Color.appCaption)
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
}
