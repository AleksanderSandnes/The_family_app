// Shared Liquid Glass building blocks (direction 1c): FAB, sheet header, chips,
// glass form field, segmented picker. Screens compose from these — don't re-roll them.
import SwiftUI

// MARK: - Extended FAB

/// Floating action button — solid accent capsule with a `+` glyph and label.
/// Caller positions it (spec: right 16, bottom 96 — above the tab bar).
struct GlassFAB: View {
    let label: String
    var systemImage = "plus"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: systemImage)
                    .font(.system(size: 16, weight: .bold))
                Text(label)
                    .font(.system(size: 15, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 20)
            .frame(height: 52)
            .background(Color.appPrimary, in: Capsule())
            .shadow(color: Color.appPrimary.opacity(0.4), radius: 12, x: 0, y: 8)
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}

// MARK: - Sheet header

/// Standard bottom-sheet header: Cancel · centered title · filled confirm capsule.
/// The confirm capsule is always legible once `confirmEnabled` — never a washed gradient.
struct SheetHeader: View {
    let title: String
    var confirmTitle = "Create"
    var confirmEnabled = true
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        ZStack {
            Text(title)
                .font(.pushedTitle)
                .foregroundStyle(Color.appOnSurface)
            HStack {
                Button("Cancel", action: onCancel)
                    .font(.system(size: 17))
                    .foregroundStyle(Color.appPrimary)
                Spacer()
                Button(action: onConfirm) {
                    Text(confirmTitle)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .frame(height: 34)
                        .background(Color.appPrimary, in: Capsule())
                        .shadow(color: Color.appPrimary.opacity(0.35), radius: 6, y: 4)
                        .opacity(confirmEnabled ? 1 : 0.4)
                }
                .disabled(!confirmEnabled)
            }
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - Chip

/// Pill chip — lead-time, reserve state, filter. Selected = solid accent, else glass.
struct GlassChip: View {
    let text: String
    var selected = false
    var action: (() -> Void)?

    var body: some View {
        let content = Text(text)
            .font(.system(size: 13, weight: selected ? .bold : .medium))
            .foregroundStyle(selected ? Color.white : Color.appOnSurfaceVariant)
            .padding(.horizontal, 14)
            .frame(height: 32)

        Group {
            if selected {
                content
                    .background(Color.appPrimary, in: Capsule())
                    .shadow(color: Color.appPrimary.opacity(0.3), radius: 8, y: 3)
            } else {
                content.glassChrome(cornerRadius: 16)
            }
        }
        .contentShape(Capsule())
        .onTapGesture { action?() }
    }
}

// MARK: - Glass form field

/// Sheet / form text field — glass surface, leading accent icon, accent caret.
struct GlassField: View {
    var systemImage: String?
    let placeholder: String
    @Binding var text: String
    var secure = false

    var body: some View {
        HStack(spacing: 12) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 22)
            }
            Group {
                if secure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(.system(size: 16))
            .foregroundStyle(Color.appOnSurface)
            .tint(Color.appPrimary)
        }
        .padding(.horizontal, 16)
        .frame(height: 54)
        .glassCard(cornerRadius: Radius.field)
    }
}

// MARK: - Segmented picker

/// Glass segmented control (Month / Week / Agenda). Selected thumb is opaque white.
struct GlassSegmentedPicker: View {
    let options: [String]
    @Binding var selection: Int

    var body: some View {
        GeometryReader { geo in
            let segW = geo.size.width / CGFloat(max(1, options.count))
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: Radius.segmentThumb, style: .continuous)
                    .fill(Color.appSurface)
                    .shadow(color: Color(hex: 0x141A3C).opacity(0.12), radius: 3, y: 1)
                    .frame(width: segW - 6, height: 32)
                    .offset(x: CGFloat(selection) * segW + 3)
                    .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selection)
                HStack(spacing: 0) {
                    ForEach(Array(options.enumerated()), id: \.offset) { idx, title in
                        Text(title)
                            .font(.system(size: 13, weight: idx == selection ? .semibold : .medium))
                            .foregroundStyle(idx == selection ? Color.appOnSurface : Color.appOnSurfaceVariant)
                            .frame(width: segW, height: 38)
                            .contentShape(Rectangle())
                            .onTapGesture { selection = idx }
                    }
                }
            }
        }
        .frame(height: 38)
        .glassChrome(cornerRadius: Radius.segment)
    }
}

// MARK: - Icon grid

/// 6-column icon picker grid (spec sheets) — selected tile is solid accent with a glow,
/// the rest are glass. Shared by the shopping / meal / wishlist / event sheets.
struct IconGrid: View {
    let options: [String]
    let selected: String
    let symbolFor: (String) -> String
    let onPick: (String) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 6)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 8) {
            ForEach(options, id: \.self) { key in
                let isSelected = key == selected
                Button { onPick(key) } label: {
                    Image(systemName: symbolFor(key))
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(isSelected ? Color.white : Color.appPrimary)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background {
                            let shape = RoundedRectangle(cornerRadius: Radius.badgeLarge, style: .continuous)
                            if isSelected {
                                shape.fill(Color.appPrimary)
                                    .shadow(color: Color.appPrimary.opacity(0.4), radius: 8, y: 3)
                            } else {
                                shape.fill(Color.appSurface.opacity(0.6))
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Standard frosted bottom-sheet chrome: grabber, frosted material background, and the
/// sheet corner radius. Apply to a sheet's root content.
extension View {
    func glassSheet(detents: Set<PresentationDetent> = [.medium]) -> some View {
        presentationDetents(detents)
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(Radius.sheet)
            .presentationBackground(.regularMaterial)
    }
}

// MARK: - Glass secondary button

/// Glass secondary CTA (e.g. "Continue with Google") — h54, glass, ink label.
struct GlassButton: View {
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
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(Color.appOnSurface)
            .frame(maxWidth: .infinity, minHeight: 54)
            .glassCard(cornerRadius: 27)
        }
        .buttonStyle(PressScaleButtonStyle())
    }
}
