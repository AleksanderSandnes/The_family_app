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

    private var iconsByDate: [LocalDate: [String]] { dateEventIcons(for: viewModel.events) }

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
                Divider().opacity(0.4)
                selectedDayList
            case .week:
                WeekStrip(
                    selectedDate: viewModel.selectedDate,
                    iconsByDate: iconsByDate,
                    onDaySelected: viewModel.selectDate
                )
                Divider().opacity(0.4)
                selectedDayList
            case .agenda:
                AgendaList(
                    events: viewModel.events,
                    onEdit: { eventToEdit = $0 },
                    onDelete: { viewModel.delete($0) }
                )
            }
        }
        .background(Color.appBackground)
        .navigationTitle("Calendar")
        .navigationBarTitleDisplayMode(.inline)
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
        Text(sectionDateLabel(viewModel.selectedDate))
            .font(.labelLarge)
            .foregroundStyle(Color.appOnSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.vertical, 10)

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

    private let dotColors: [Color] = [
        .appPrimary, .appSecondary, .appTertiary, .appError,
        .appPrimaryContainer, .appSecondaryContainer,
    ]

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                if let date {
                    ZStack {
                        Circle()
                            .fill(backgroundColor)
                            .frame(width: 34, height: 34)
                        if isSelected && !isToday {
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
                let l = LocalDate(iso: lhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                let r = LocalDate(iso: rhs.dateFrom) ?? LocalDate(year: 9999, month: 12, day: 31)
                return l < r
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

    private let containerColors: [Color] = [
        .appPrimaryContainer, .appSecondaryContainer,
        Palette.pink500.opacity(0.18), Color.appError.opacity(0.15),
    ]
    private let contentColors: [Color] = [
        .appOnPrimaryContainer, .appOnSecondaryContainer, Palette.pink500, .appError,
    ]

    var body: some View {
        let index = calendarIconColorIndex(event.icon) % containerColors.count
        let timeLabel = eventTimeLabel(event)
        Button(action: onEdit) {
            HStack(spacing: Spacing.md) {
                Image(systemName: IconKeyMap.calendarSymbol(event.icon))
                    .font(.system(size: 18))
                    .foregroundStyle(contentColors[index])
                    .frame(width: 42, height: 42)
                    .background(containerColors[index])
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(event.activity)
                        .font(.titleMedium)
                        .foregroundStyle(Color.appOnSurface)
                    if !timeLabel.isEmpty {
                        Text(timeLabel)
                            .font(.labelMedium)
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, 14)
            .background(Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: Elevation.resting, y: 1)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Event sheet (add/edit)

private struct EventSheet: View {
    let existingEvent: CalendarEventModel?
    let initialDate: LocalDate
    let onSave: (EventDraft) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var activity: String
    @State private var allDay: Bool
    @State private var selectedIcon: String
    @State private var showIconPicker = false
    @State private var dateFrom: LocalDate
    @State private var dateTo: LocalDate
    @State private var timeFrom: Date
    @State private var timeTo: Date

    init(
        existingEvent: CalendarEventModel?,
        initialDate: LocalDate,
        onSave: @escaping (EventDraft) -> Void
    ) {
        self.existingEvent = existingEvent
        self.initialDate = initialDate
        self.onSave = onSave
        _activity = State(initialValue: existingEvent?.activity ?? "")
        _allDay = State(initialValue: existingEvent?.allDay ?? false)
        _selectedIcon = State(initialValue: existingEvent?.icon ?? "schedule")
        let from = existingEvent.flatMap { LocalDate(iso: $0.dateFrom) } ?? initialDate
        let to = existingEvent.flatMap { LocalDate(iso: $0.dateTo) } ?? initialDate
        _dateFrom = State(initialValue: from)
        _dateTo = State(initialValue: to)
        _timeFrom = State(initialValue: Self.time(existingEvent?.timeFrom, defaultHour: 9))
        _timeTo = State(initialValue: Self.time(existingEvent?.timeTo, defaultHour: 10))
    }

    private static func time(_ stored: String?, defaultHour: Int) -> Date {
        let parts = stored?.split(separator: ":").compactMap { Int($0) } ?? []
        let hour = parts.count == 2 ? min(max(parts[0], 0), 23) : defaultHour
        let minute = parts.count == 2 ? min(max(parts[1], 0), 59) : 0
        return Calendar.current.date(
            bySettingHour: hour, minute: minute, second: 0, of: Date()
        ) ?? Date()
    }

    private static func timeString(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(existingEvent != nil ? "Edit event" : "New event")
                    .font(.titleLarge)
                    .foregroundStyle(Color.appOnSurface)
                    .padding(.top, Spacing.xxl)

                HStack(spacing: Spacing.md) {
                    Button {
                        withAnimation { showIconPicker.toggle() }
                    } label: {
                        Image(systemName: IconKeyMap.calendarSymbol(selectedIcon))
                            .font(.system(size: 22))
                            .foregroundStyle(Color.appOnPrimaryContainer)
                            .frame(width: 48, height: 48)
                            .background(Color.appPrimaryContainer)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .accessibilityLabel("Change icon")
                    FamilyTextField(label: "Event name", text: $activity)
                }

                if showIconPicker {
                    let columns = Array(repeating: GridItem(.flexible()), count: 4)
                    LazyVGrid(columns: columns, spacing: Spacing.sm) {
                        ForEach(IconOptions.calendar, id: \.self) { key in
                            Button {
                                selectedIcon = key
                                withAnimation { showIconPicker = false }
                            } label: {
                                Image(systemName: IconKeyMap.calendarSymbol(key))
                                    .font(.system(size: 20))
                                    .foregroundStyle(
                                        key == selectedIcon
                                            ? Color.appOnPrimaryContainer : Color.appOnSurfaceVariant
                                    )
                                    .frame(maxWidth: .infinity, minHeight: 52)
                                    .background(
                                        key == selectedIcon
                                            ? Color.appPrimaryContainer : Color.appSurfaceVariant
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                        }
                    }
                }

                Divider()
                Toggle("All day", isOn: $allDay)
                    .font(.bodyLarge)
                Divider()

                dateTimeRow(label: "Starts", date: $dateFrom, time: $timeFrom) { picked in
                    if dateTo < picked { dateTo = picked }
                }
                Divider()
                dateTimeRow(label: "Ends", date: $dateTo, time: $timeTo) { picked in
                    if picked < dateFrom { dateTo = dateFrom }
                }
                Divider()

                PrimaryButton(
                    text: "Save",
                    enabled: !activity.trimmingCharacters(in: .whitespaces).isEmpty
                ) {
                    onSave(EventDraft(
                        activity: activity.trimmingCharacters(in: .whitespaces),
                        allDay: allDay,
                        dateFrom: dateFrom.isoString,
                        dateTo: dateTo.isoString,
                        timeFrom: allDay ? "" : Self.timeString(timeFrom),
                        timeTo: allDay ? "" : Self.timeString(timeTo),
                        icon: selectedIcon
                    ))
                    dismiss()
                }
                .padding(.top, Spacing.sm)
            }
            .padding(.horizontal, Spacing.screenEdge)
            .padding(.bottom, Spacing.lg)
        }
        .presentationDetents([.large])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
    }

    @ViewBuilder
    private func dateTimeRow(
        label: String,
        date: Binding<LocalDate>,
        time: Binding<Date>,
        onDatePicked: @escaping (LocalDate) -> Void
    ) -> some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
            Spacer()
            EventDateButton(selection: date, onPicked: onDatePicked)
            if !allDay {
                DatePicker("", selection: time, displayedComponents: .hourAndMinute)
                    .labelsHidden()
            }
        }
    }
}

/// Compact "EEE d MMM" date button that opens a graphical picker.
private struct EventDateButton: View {
    @Binding var selection: LocalDate
    var onPicked: (LocalDate) -> Void

    @State private var showPicker = false
    @State private var draft = Date()

    var body: some View {
        Button {
            draft = Date(timeIntervalSince1970: TimeInterval(selection.epochDay) * 86_400)
            showPicker = true
        } label: {
            Text(selection.formattedShort())
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurface)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.appSurfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker("Date", selection: $draft, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: "OK") {
                    var utc = Calendar(identifier: .gregorian)
                    utc.timeZone = .current
                    let parts = utc.dateComponents([.year, .month, .day], from: draft)
                    let picked = LocalDate(year: parts.year!, month: parts.month!, day: parts.day!)
                    selection = picked
                    onPicked(picked)
                    showPicker = false
                }
                .padding(.horizontal, Spacing.screenEdge)
            }
            .padding(.bottom, Spacing.lg)
            .presentationDetents([.medium, .large])
            .presentationCornerRadius(Radius.sheet)
        }
    }
}
