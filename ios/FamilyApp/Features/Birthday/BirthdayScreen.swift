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
                        title: "No birthdays",
                        subtitle: "Add family birthdays so you never miss a celebration.",
                        actionLabel: "Add birthday"
                    ) { showAdd = true }
                } else {
                    List {
                        ForEach(sorted) { birthday in
                            BirthdayCard(birthday: birthday, today: today) { editing = birthday }
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

            FloatingActionButton(text: "Add birthday", systemImage: "plus") { showAdd = true }
        }
        .ambientBackground()
        .featureTopBar("Birthdays")
        .resumeEffect { viewModel.refresh() }
        .sheet(isPresented: $showAdd) {
            BirthdaySheet(title: "Add birthday", confirmLabel: "Add") { name, date in
                viewModel.add(name: name, date: date)
                showAdd = false
            }
        }
        .sheet(item: $editing) { birthday in
            BirthdaySheet(
                title: "Edit birthday",
                confirmLabel: "Save",
                initialName: birthday.name,
                initialDate: birthday.date
            ) { name, date in
                viewModel.update(id: birthday.id, name: name, date: date)
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
        let displayDate = next.map(formatFullDate) ?? birthday.date
        let isToday = daysUntil == 0
        let thisWeek = (daysUntil ?? 999) >= 1 && (daysUntil ?? 999) <= 7
        let later = !isToday && !thisWeek

        Button(action: onEdit) {
            HStack(spacing: 12) {
                FeatureBadge(
                    systemImage: "birthday.cake.fill",
                    feature: .birthdays,
                    size: 46,
                    cornerRadius: 23
                )
                .saturation(later ? 0.35 : 1)
                .opacity(later ? 0.85 : 1)
                VStack(alignment: .leading, spacing: 3) {
                    Text(birthday.name)
                        .font(.system(size: 15.5, weight: .semibold))
                        .foregroundStyle(later ? Color.appOnSurfaceVariant : Color.appOnSurface)
                    HStack(spacing: 5) {
                        Text(displayDate)
                            .foregroundStyle(Color.appCaption)
                        if let age {
                            Text("· turning \(age)")
                                .foregroundStyle(later ? Color.appCaption : FeatureAccent.birthdays.stroke)
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
            .modifier(BirthdaySurface(isToday: isToday, later: later))
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

    /// Urgency-driven surface: today = glass + green ring, this week = glass, later = ghost.
    private struct BirthdaySurface: ViewModifier {
        let isToday: Bool
        let later: Bool
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
                content.rowSurface(ghost: later, cornerRadius: Radius.overviewCard)
            }
        }
    }

    private func formatFullDate(_ date: LocalDate) -> String {
        let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM d, yyyy"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: instant)
    }
}

private struct BirthdaySheet: View {
    let title: String
    let confirmLabel: String
    var initialName = ""
    var initialDate = ""
    let onConfirm: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var date = ""

    private var canConfirm: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && !date.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            SheetHeader(title: title, confirmTitle: confirmLabel, confirmEnabled: canConfirm) {
                dismiss()
            } onConfirm: {
                onConfirm(name.trimmingCharacters(in: .whitespaces), date)
                dismiss()
            }
            GlassField(systemImage: "person", placeholder: "Name", text: $name)
            BirthdayPickerField(isoDate: $date, label: "Birthday *")
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.top, Spacing.lg)
        .padding(.bottom, Spacing.lg)
        .glassSheet(detents: [.medium])
        .onAppear {
            name = initialName
            date = initialDate
        }
    }
}
