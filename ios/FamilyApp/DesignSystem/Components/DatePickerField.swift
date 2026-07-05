// Click-to-open date picker field — mirrors DatePickerField in Components.kt.
import SwiftUI

struct DatePickerField: View {
    let label: String
    @Binding var date: Date
    var displayedComponents: DatePickerComponents = .date

    @State private var showPicker = false

    var body: some View {
        Button {
            showPicker = true
        } label: {
            HStack {
                Image(systemName: "calendar")
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Text(label)
                    .font(.bodyLarge)
                    .foregroundStyle(Color.appOnSurfaceVariant)
                Spacer()
                Text(date.formatted(date: .abbreviated, time: displayedComponents == .date ? .omitted : .shortened))
                    .font(.bodyLarge)
                    .foregroundStyle(Color.appOnSurface)
            }
            .padding(.horizontal, Spacing.lg)
            .frame(minHeight: 56)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(Color.appOnSurface.opacity(0.45), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showPicker) {
            VStack(spacing: Spacing.lg) {
                DatePicker(label, selection: $date, displayedComponents: displayedComponents)
                    .datePickerStyle(.graphical)
                    .padding(Spacing.lg)
                PrimaryButton(text: "Done") { showPicker = false }
                    .padding(.horizontal, Spacing.screenEdge)
            }
            .padding(.bottom, Spacing.lg)
            .presentationDetents([.medium, .large])
            .presentationCornerRadius(Radius.sheet)
        }
    }
}
