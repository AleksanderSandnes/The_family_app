// Premium text field used across auth and forms — mirrors FamilyTextField in
// Components.kt (outlined, rounded, reveal toggle for passwords).
import SwiftUI

struct FamilyTextField: View {
    let label: String
    @Binding var text: String
    var systemImage: String?
    var isPassword = false
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var autocapitalization: TextInputAutocapitalization = .sentences
    var supportingText: String?
    var isError = false
    /// Solid near-white fill (used on the purple auth hero) instead of translucent glass.
    var whiteField = false

    @State private var revealed = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            let field = fieldRow
                .padding(.horizontal, 16)
                .frame(height: 54)
            Group {
                if whiteField {
                    field
                        .background(
                            RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                                .fill(Color(light: .white, dark: Palette.inkSurfaceVariant))
                                .shadow(color: Color(hex: 0x141A3C).opacity(0.06), radius: 6, y: 2)
                        )
                } else {
                    field.glassCard(cornerRadius: Radius.field)
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(borderColor, lineWidth: 1.5)
            )

            if let supportingText {
                Text(supportingText)
                    .font(.labelMedium)
                    .foregroundStyle(isError ? Color.appError : Color.appOnSurfaceVariant)
                    .padding(.horizontal, Spacing.xs)
            }
        }
    }

    private var fieldRow: some View {
        HStack(spacing: 12) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 22)
            }
            Group {
                if isPassword, !revealed {
                    SecureField(label, text: $text)
                } else {
                    TextField(label, text: $text)
                }
            }
            .font(.system(size: 16))
            .tint(Color.appPrimary)
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
                        .foregroundStyle(Color.appCaption)
                }
            }
        }
    }

    private var borderColor: Color {
        if isError { return .appError }
        if focused { return Color.appPrimary.opacity(0.5) }
        return .clear
    }
}
