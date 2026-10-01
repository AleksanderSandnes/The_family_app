import SwiftUI

/// Read-only field that opens a date picker sheet; stores ISO-8601 (yyyy-MM-dd).
struct BirthdayPickerField: View {
    @Binding var isoDate: String
    var label = "Birthday (optional)"

    @State private var showPicker = false
    @State private var selection = Date()

    private static let isoFormat: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    var body: some View {
        Button {
            let fallback = Calendar.current.date(byAdding: .year, value: -30, to: .now) ?? .now
            selection = Self.isoFormat.date(from: isoDate) ?? fallback
            showPicker = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "birthday.cake")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 22)
                Text(displayText.isEmpty ? label : displayText)
                    .font(.system(size: 16))
                    .foregroundStyle(displayText.isEmpty ? Color.appCaption : Color.appOnSurface)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.appCaption)
            }
            .padding(.horizontal, 16)
            .frame(height: 54)
            .glassCard(cornerRadius: Radius.field)
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker("Birthday", selection: $selection, displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: L("Done")) {
                    isoDate = Self.isoFormat.string(from: selection)
                    showPicker = false
                }
                .padding(.horizontal, Spacing.screenEdge)
            }
            .padding(.bottom, Spacing.lg)
            .presentationDetents([.medium, .large])
            .presentationCornerRadius(Radius.sheet)
        }
    }

    private var displayText: String {
        guard let date = Self.isoFormat.date(from: isoDate) else { return "" }
        return date.formatted(.dateTime.month(.wide).day().year().locale(appLocale))
    }
}
