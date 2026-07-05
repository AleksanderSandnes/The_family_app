// Premium text field used across auth and forms — mirrors FamilyTextField in
// Components.kt (outlined, rounded, reveal toggle for passwords).
import SwiftUI

struct FamilyTextField: View {
    let label: String
    @Binding var text: String
    var systemImage: String?
    var isPassword: Bool = false
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var autocapitalization: TextInputAutocapitalization = .sentences
    var supportingText: String?
    var isError: Bool = false

    @State private var revealed = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(spacing: Spacing.md) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .foregroundStyle(focused ? Color.appPrimary : Color.appOnSurfaceVariant)
                }
                Group {
                    if isPassword && !revealed {
                        SecureField(label, text: $text)
                    } else {
                        TextField(label, text: $text)
                    }
                }
                .font(.bodyLarge)
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .textInputAutocapitalization(isPassword ? .never : autocapitalization)
                .autocorrectionDisabled(isPassword)
                .focused($focused)

                if isPassword {
                    Button {
                        revealed.toggle()
                    } label: {
                        Image(systemName: revealed ? "eye.slash" : "eye")
                            .foregroundStyle(Color.appOnSurfaceVariant)
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
            .frame(minHeight: 56)
            .overlay(
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(borderColor, lineWidth: focused ? 2 : 1)
            )

            if let supportingText {
                Text(supportingText)
                    .font(.labelMedium)
                    .foregroundStyle(isError ? Color.appError : Color.appOnSurfaceVariant)
                    .padding(.horizontal, Spacing.xs)
            }
        }
    }

    private var borderColor: Color {
        if isError { return .appError }
        if focused { return .appPrimary }
        return Color.appOnSurface.opacity(0.45)
    }
}
