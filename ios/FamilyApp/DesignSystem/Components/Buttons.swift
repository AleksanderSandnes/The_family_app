// Button components — mirror Components.kt (PrimaryButton / SecondaryButton /
// DestructiveButton). Same names, same visual rules.
import SwiftUI

/// Primary gradient call-to-action button with built-in loading state.
struct PrimaryButton: View {
    let text: String
    var enabled = true
    var loading = false
    var systemImage: String?
    let action: () -> Void

    @GestureState private var pressed = false

    private var active: Bool {
        enabled && !loading
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                if loading {
                    ProgressView()
                        .tint(.white)
                } else {
                    HStack(spacing: Spacing.sm) {
                        if let systemImage {
                            Image(systemName: systemImage)
                                .font(.system(size: 17, weight: .semibold))
                        }
                        Text(text)
                            .font(.titleMedium)
                    }
                    .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            // Solid accent (Liquid Glass 1c) — the brand gradient is reserved for
            // identity surfaces only. Accent glow underneath.
            .background(Color.appPrimary, in: RoundedRectangle(cornerRadius: 27, style: .continuous))
            .accentGlow(active: active, opacity: 0.35, radius: 11, y: 8)
            // Disabled = the SAME accent at 38% opacity — never a flat dead-gray.
            .opacity(active ? 1 : 0.38)
        }
        .buttonStyle(PressScaleButtonStyle())
        .disabled(!active)
    }
}

/// Subtle outlined secondary action.
struct SecondaryButton: View {
    let text: String
    var systemImage: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.appPrimary)
                }
                Text(text)
                    .font(.titleMedium)
                    .foregroundStyle(Color.appOnSurface)
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                    .strokeBorder(Color.appOutline, lineWidth: 1)
            )
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

/// Destructive full-width action (Leave family, Sign out, Delete). Tonal red.
struct DestructiveButton: View {
    let text: String
    var systemImage: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 17, weight: .semibold))
                }
                Text(text)
                    .font(.titleMedium.weight(.semibold))
            }
            .foregroundStyle(Color.appError)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.appError.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

/// Press feedback matching Android's 0.97 scale-on-press.
struct PressScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
