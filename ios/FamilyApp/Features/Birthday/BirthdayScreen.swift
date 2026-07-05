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
        .background(Color.appBackground)
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

        ListCard(onTap: onEdit) {
            Image(systemName: "birthday.cake.fill")
                .font(.system(size: 20))
                .foregroundStyle(Color.appPrimary)
                .frame(width: 40, height: 40)
                .background(Color.appPrimaryContainer)
                .clipShape(Circle())
            Spacer().frame(width: 14)
            VStack(alignment: .leading, spacing: 2) {
                Text(birthday.name)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
                Text(displayDate)
                    .font(.bodyMedium)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                if let age {
                    Text("Turning \(age)")
                        .font(.labelMedium)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 3)
                        .background(Palette.pink500.opacity(0.18))
                        .foregroundStyle(Palette.pink500)
                        .clipShape(Capsule())
                }
            }
            Spacer()
            if let daysUntil {
                daysPill(daysUntil)
            }
        }
        .accessibilityLabel("\(birthday.name)'s birthday, \(displayDate)")
    }

    @ViewBuilder
    private func daysPill(_ days: Int) -> some View {
        let (text, container, content): (String, Color, Color) = switch days {
        case 0: ("Today!", Color(hex: 0x22C55E), .white)
        case 1...7: ("In \(days) days", Palette.amber500.opacity(0.18), Color(hex: 0xB45309))
        default: ("In \(days) days", .appSurfaceVariant, .appOnSurfaceVariant)
        }
        Text(text)
            .font(.labelMedium.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(container)
            .foregroundStyle(content)
            .clipShape(Capsule())
    }

    private func formatFullDate(_ date: LocalDate) -> String {
        let instant = Date(timeIntervalSince1970: TimeInterval(date.epochDay) * 86_400)
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

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title)
                .font(.titleLarge)
                .foregroundStyle(Color.appOnSurface)
                .padding(.top, Spacing.xxl)
            FamilyTextField(label: "Name", text: $name, systemImage: "person")
            BirthdayPickerField(isoDate: $date, label: "Birthday *")
            Spacer()
            PrimaryButton(
                text: confirmLabel,
                enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !date.isEmpty
            ) {
                onConfirm(name.trimmingCharacters(in: .whitespaces), date)
                dismiss()
            }
        }
        .padding(.horizontal, Spacing.screenEdge)
        .padding(.bottom, Spacing.lg)
        .presentationDetents([.medium])
        .presentationCornerRadius(Radius.sheet)
        .background(Color.appBackground)
        .onAppear {
            name = initialName
            date = initialDate
        }
    }
}
