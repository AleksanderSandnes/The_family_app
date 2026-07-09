// Event add/edit sheet extracted from CalendarScreen.swift: the form for creating or
// editing a calendar event (icon picker, all-day toggle, date/time ranges) plus the
// compact date button that opens a graphical picker.
import SwiftUI

struct EventSheet: View {
    let existingEvent: CalendarEventModel?
    let initialDate: LocalDate
    /// Family members (excluding self) selectable in the "Going with" picker.
    let members: [UserModel]
    let onSave: (EventDraft) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var activity: String
    @State private var allDay: Bool
    @State private var isPrivate: Bool
    @State private var color: Int?
    @State private var attendeeIds: Set<String>
    @State private var showAttendeePicker = false
    @State private var selectedIcon: String
    @State private var showIconPicker = false
    @State private var dateFrom: LocalDate
    @State private var dateTo: LocalDate
    @State private var timeFrom: Date
    @State private var timeTo: Date

    init(
        existingEvent: CalendarEventModel?,
        initialDate: LocalDate,
        members: [UserModel] = [],
        onSave: @escaping (EventDraft) -> Void
    ) {
        self.existingEvent = existingEvent
        self.initialDate = initialDate
        self.members = members
        self.onSave = onSave
        _attendeeIds = State(initialValue: Set(existingEvent?.attendeeIds ?? []))
        _activity = State(initialValue: existingEvent?.activity ?? "")
        _allDay = State(initialValue: existingEvent?.allDay ?? false)
        _isPrivate = State(initialValue: existingEvent?.isPrivate ?? false)
        // New events default to the first palette colour; edits keep their stored colour.
        _color = State(initialValue: existingEvent.map(\.color) ?? calendarEventColorPalette.first)
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

    private var canSave: Bool {
        !activity.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private func save() {
        onSave(EventDraft(
            activity: activity.trimmingCharacters(in: .whitespaces),
            allDay: allDay,
            dateFrom: dateFrom.isoString,
            dateTo: dateTo.isoString,
            timeFrom: allDay ? "" : Self.timeString(timeFrom),
            timeTo: allDay ? "" : Self.timeString(timeTo),
            icon: selectedIcon,
            isPrivate: isPrivate,
            color: color,
            attendeeIds: members.map(\.id).filter { attendeeIds.contains($0) }
        ))
        dismiss()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(
                title: existingEvent != nil ? L("Edit event") : L("New event"),
                confirmTitle: L("Save"), confirmEnabled: canSave,
                onCancel: { dismiss() }, onConfirm: save
            )
            GlassField(
                systemImage: IconKeyMap.calendarSymbol(selectedIcon),
                placeholder: L("Event name"),
                text: $activity
            )
            SectionHeader(text: L("Icon"))
            IconGrid(
                options: IconOptions.calendar,
                selected: selectedIcon,
                symbolFor: IconKeyMap.calendarSymbol
            ) { selectedIcon = $0 }

            SectionHeader(text: L("Color"))
            EventColorPicker(selection: $color)

            VStack(spacing: 0) {
                Toggle(L("Private"), isOn: $isPrivate)
                    .font(.system(size: 16))
                    .tint(Color.appPrimary)
                    .padding(.vertical, 14)
                Divider()
                Toggle("All day", isOn: $allDay)
                    .font(.system(size: 16))
                    .tint(Color.appPrimary)
                    .padding(.vertical, 14)
                Divider()
                dateTimeRow(label: L("Starts"), date: $dateFrom, time: $timeFrom) { picked in
                    if dateTo < picked { dateTo = picked }
                }
                .padding(.vertical, 14)
                Divider()
                dateTimeRow(label: L("Ends"), date: $dateTo, time: $timeTo) { picked in
                    if picked < dateFrom { dateTo = dateFrom }
                }
                .padding(.vertical, 14)
                if !members.isEmpty {
                    Divider()
                    goingWithRow
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, 4)
            .glassCard(cornerRadius: Radius.menu)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
        .sheet(isPresented: $showAttendeePicker) {
            AttendeePickerSheet(members: members, selection: $attendeeIds)
        }
    }

    /// Compact one-line row that opens the full attendee picker in its own sheet.
    private var goingWithRow: some View {
        Button { showAttendeePicker = true } label: {
            HStack {
                Text(L("Going with"))
                    .font(.system(size: 16))
                    .foregroundStyle(Color.appOnSurface)
                Spacer()
                let selected = members.filter { attendeeIds.contains($0.id) }
                if selected.isEmpty {
                    Text(L("Add"))
                        .font(.system(size: 15))
                        .foregroundStyle(Color.appPrimary)
                } else {
                    HStack(spacing: -8) {
                        ForEach(selected.prefix(4)) { member in
                            InitialAvatar(user: member, size: 26)
                                .overlay(Circle().strokeBorder(Color.appSurface, lineWidth: 2))
                        }
                    }
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

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

/// Full attendee multi-select, presented as its own sheet from the compact "Going with" row
/// so the event form itself stays short.
private struct AttendeePickerSheet: View {
    let members: [UserModel]
    @Binding var selection: Set<String>
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(
                title: L("Going with"), confirmTitle: L("Done"), confirmEnabled: true,
                onCancel: { dismiss() }, onConfirm: { dismiss() }
            )
            VStack(spacing: 0) {
                ForEach(Array(members.enumerated()), id: \.element.id) { index, member in
                    Button {
                        if selection.contains(member.id) {
                            selection.remove(member.id)
                        } else {
                            selection.insert(member.id)
                        }
                    } label: {
                        HStack(spacing: Spacing.md) {
                            InitialAvatar(user: member, size: 40)
                            Text(member.name)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(Color.appOnSurface)
                            Spacer()
                            Image(systemName: selection.contains(member.id) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 22))
                                .foregroundStyle(selection.contains(member.id) ? Color.appPrimary : Color.appCaption)
                        }
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if index < members.count - 1 {
                        Divider().padding(.leading, 68)
                    }
                }
            }
            .glassCard(cornerRadius: Radius.row)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
    }
}

/// On-brand horizontal swatch picker for an event/birthday colour (shared across features).
struct EventColorPicker: View {
    @Binding var selection: Int?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(calendarEventColorPalette, id: \.self) { hex in
                    let swatch = Color(hex: UInt32(truncatingIfNeeded: hex))
                    let isSelected = selection == hex
                    Button { selection = hex } label: {
                        Circle()
                            .fill(swatch)
                            .frame(width: 30, height: 30)
                            .overlay {
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundStyle(.white)
                                }
                            }
                            .overlay {
                                Circle()
                                    .strokeBorder(swatch, lineWidth: isSelected ? 2 : 0)
                                    .padding(-3)
                            }
                            .scaleEffect(isSelected ? 1.08 : 1)
                            .animation(.snappy(duration: 0.15), value: isSelected)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Event colour")
                }
            }
            // Enough inset that the selected swatch's outward ring + scale never clip
            // against the scroll view's edges.
            .padding(.vertical, 8)
            .padding(.horizontal, 8)
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
            draft = Date(timeIntervalSince1970: TimeInterval(selection.epochDay) * 86400)
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
                PrimaryButton(text: L("OK")) {
                    var utc = Calendar(identifier: .gregorian)
                    utc.timeZone = .current
                    let parts = utc.dateComponents([.year, .month, .day], from: draft)
                    guard let year = parts.year, let month = parts.month, let day = parts.day else {
                        return
                    }
                    let picked = LocalDate(year: year, month: month, day: day)
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
