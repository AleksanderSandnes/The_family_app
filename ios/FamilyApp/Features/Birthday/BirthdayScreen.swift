// Birthdays — the iOS twin of BirthdayScreen.kt: countdown cards sorted by next
// occurrence, "Turning N" chip, urgency-tinted days pill, add/edit sheets.
import SwiftUI

struct BirthdayScreen: View {
    let viewModel: BirthdayViewModel

    @State private var showAdd = false
    @State private var editing: BirthdayModel?

    var body: some View {
        let today = LocalDate.today()
        let sorted = sortedByNextBirthday(viewModel.birthdays, today: today)

        ZStack(alignment: .bottomTrailing) {
            Group {
                if viewModel.isLoading {
                    LoadingState().frame(maxHeight: .infinity, alignment: .top)
                } else if sorted.isEmpty {
                    EmptyState(
                        systemImage: "birthday.cake.fill",
                        title: L("No birthdays"),
                        subtitle: L("Add family birthdays so you never miss a celebration.")
                    )
                } else {
                    List {
                        ForEach(sorted) { birthday in
                            BirthdayCard(birthday: birthday, today: today) {
                                // Only the creator may edit (auto-birthdays belong to the person).
                                if birthday.madeByUserId == viewModel.currentUserId { editing = birthday }
                            }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .listRowInsets(EdgeInsets(
                                    top: 6, leading: Spacing.screenEdge,
                                    bottom: 6, trailing: Spacing.screenEdge
                                ))
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.delete(birthday)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable { viewModel.refresh() }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            FloatingActionButton(text: L("Add birthday"), systemImage: "plus") { showAdd = true }
        }
        .ambientBackground()
        .featureTopBar(L("Birthdays"))
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            BirthdaySheet(title: L("Add birthday"), confirmLabel: L("Add")) { name, date, icon, color in
                viewModel.add(name: name, date: date, icon: icon, color: color)
                showAdd = false
            }
        }
        .sheet(item: $editing) { birthday in
            BirthdaySheet(
                title: L("Edit birthday"),
                confirmLabel: L("Save"),
                initialName: birthday.name,
                initialDate: birthday.date,
                initialIcon: birthday.icon,
                initialColor: birthday.color
            ) { name, date, icon, color in
                viewModel.update(id: birthday.id, name: name, date: date, icon: icon, color: color)
                editing = nil
            }
        }
    }
}

private struct BirthdayCard: View {
    let birthday: BirthdayModel
    let today: LocalDate
    let onEdit: () -> Void

    var body: some View {
        let next = nextBirthdayDate(birthday.date, today: today)
        let age = turnsAge(birthday.date, today: today)
        let daysUntil = next.map { today.daysUntil($0) }
        // Show the actual date of birth (year included), not the next occurrence.
        let displayDate = LocalDate(iso: birthday.date).map(formatFullDate) ?? birthday.date
        let isToday = daysUntil == 0
        let accent = birthdayAccent(birthday)

        Button(action: onEdit) {
            HStack(spacing: 12) {
                Circle()
                    .fill(accent.opacity(0.16))
                    .frame(width: 46, height: 46)
                    .overlay(
                        Image(systemName: IconKeyMap.calendarSymbol(birthday.icon))
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(accent)
                    )
                VStack(alignment: .leading, spacing: 3) {
                    Text(birthday.name)
                        .font(.system(size: 15.5, weight: .semibold))
                        .foregroundStyle(Color.appOnSurface)
                    HStack(spacing: 5) {
                        Text(displayDate)
                            .foregroundStyle(Color.appCaption)
                        if let age {
                            Text("· turning \(age)")
                                .foregroundStyle(FeatureAccent.birthdays.stroke)
                        }
                    }
                    .font(.system(size: 12.5, weight: .medium))
                }
                Spacer()
                if let daysUntil {
                    daysPill(daysUntil)
                }
            }
            .padding(Spacing.cardPadding)
            .modifier(BirthdaySurface(isToday: isToday))
        }
        .buttonStyle(PressScaleButtonStyle())
        .accessibilityLabel("\(birthday.name)'s birthday, \(displayDate)")
    }

    @ViewBuilder
    private func daysPill(_ days: Int) -> some View {
        switch days {
        case 0:
            Text("Today! 🎉")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 11).padding(.vertical, 5)
                .background(Color.appSuccess, in: Capsule())
                .shadow(color: Color.appSuccess.opacity(0.35), radius: 8, y: 3)
        case 1...7:
            Text("In \(days) days")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Palette.weekAmberText)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(Palette.amber500.opacity(0.16), in: Capsule())
        default:
            Text("In \(days) days")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.appCaption)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(Color(hex: 0x8B92AC).opacity(0.12), in: Capsule())
        }
    }

    /// Today = glass + green ring; all other (future/this week) = solid glass card.
    private struct BirthdaySurface: ViewModifier {
        let isToday: Bool
        func body(content: Content) -> some View {
            if isToday {
                content
                    .glassCard(cornerRadius: Radius.overviewCard)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.overviewCard, style: .continuous)
                            .strokeBorder(Color.appSuccess.opacity(0.5), lineWidth: 1.5)
                    )
                    .shadow(color: Color.appSuccess.opacity(0.18), radius: 12, y: 8)
            } else {
                content.rowSurface(ghost: false, cornerRadius: Radius.overviewCard)
            }
        }
    }

    private func formatFullDate(_ date: LocalDate) -> String {
        let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = appLocale
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: instant)
    }
}

/// A birthday's badge colour — the user-picked colour, else the birthdays accent.
func birthdayAccent(_ birthday: BirthdayModel) -> Color {
    if let hex = birthday.color { return Color(hex: UInt32(truncatingIfNeeded: hex)) }
    return FeatureAccent.birthdays.stroke
}

private struct BirthdaySheet: View {
    let title: String
    let confirmLabel: String
    var initialName = ""
    var initialDate = ""
    var initialIcon = "cake"
    var initialColor: Int?
    let onConfirm: (String, String, String, Int?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var date = ""
    @State private var icon = "cake"
    @State private var color: Int?

    private var canConfirm: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && !date.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: title, confirmTitle: confirmLabel, confirmEnabled: canConfirm) {
                dismiss()
            } onConfirm: {
                onConfirm(name.trimmingCharacters(in: .whitespaces), date, icon, color)
                dismiss()
            }
            GlassField(systemImage: "person", placeholder: L("Name"), text: $name)
            BirthdayPickerField(isoDate: $date, label: L("Birthday *"))

            SectionHeader(text: L("Icon"))
            IconGrid(
                options: IconOptions.calendar,
                selected: icon,
                symbolFor: IconKeyMap.calendarSymbol
            ) { icon = $0 }

            SectionHeader(text: L("Color"))
            EventColorPicker(selection: $color)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.xl)
        .huggingSheet()
        .onAppear {
            name = initialName
            date = initialDate
            icon = initialIcon
            color = initialColor
        }
    }
}
