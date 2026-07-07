// Liquid Glass surfaces (direction 1c), built on the native iOS 26 `.glassEffect` API.
// The design's CSS "backdrop blur + tint + inset shine + hairline border + drop shadow"
// recipe is exactly what the system Liquid Glass material renders — so we lean on the
// real material and only add the soft floating drop shadow the spec calls for.
import SwiftUI

extension View {
    /// Content card / list row / grid tile — recipe A. The workhorse surface.
    /// `contentShape` pins the hit region to the card — the iOS 26 glass effect
    /// otherwise leaves a wrapping Button's tap target misaligned.
    func glassCard(cornerRadius: CGFloat = Radius.card) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return glassEffect(.regular, in: shape)
            .shadow(color: Color(hex: 0x141A3C).opacity(0.06), radius: 9, x: 0, y: 6)
            .contentShape(shape)
    }

    /// Accent-tinted glass card — for selected / active surfaces.
    func glassCardTinted(_ tint: Color, cornerRadius: CGFloat = Radius.card) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return glassEffect(.regular.tint(tint), in: shape)
            .shadow(color: tint.opacity(0.2), radius: 9, x: 0, y: 6)
            .contentShape(shape)
    }

    /// Nav / chrome pill or floating control — recipe B. Interactive (reacts to touch).
    func glassChrome(cornerRadius: CGFloat) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return glassEffect(.regular.interactive(), in: shape)
    }

    /// Circular glass chrome control (compose, mic, locate buttons).
    func glassCircle() -> some View {
        glassEffect(.regular.interactive(), in: Circle())
    }

    /// Picks glass (active) or dashed ghost (completed/empty) for a list-row surface.
    @ViewBuilder
    func rowSurface(ghost: Bool, cornerRadius: CGFloat = Radius.row) -> some View {
        if ghost {
            ghostSurface(cornerRadius: cornerRadius)
        } else {
            glassCard(cornerRadius: cornerRadius)
        }
    }

    /// Ghost / empty surface — completed or unplanned items recede: no glass material,
    /// a translucent fill and a dashed hairline, no drop shadow.
    func ghostSurface(cornerRadius: CGFloat = Radius.card) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        return background(
            Color(light: .white.opacity(0.36), dark: .white.opacity(0.04)),
            in: shape
        )
        .overlay(
            shape.strokeBorder(
                Color(light: Color(hex: 0x5F6780).opacity(0.28), dark: .white.opacity(0.12)),
                style: StrokeStyle(lineWidth: 1, dash: [4, 3])
            )
        )
        .contentShape(shape)
    }
}

/// A rounded feature icon badge — translucent domain-coloured fill with the glyph in the
/// domain stroke colour. Replaces gradient icon squares; feature colour lives only here.
struct FeatureBadge: View {
    let systemImage: String
    let feature: FeatureAccent
    var size: CGFloat = 38
    var cornerRadius: CGFloat = 12
    /// When set, overrides the feature palette with a user-picked colour.
    var colorOverride: Color?

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(colorOverride?.opacity(0.16) ?? feature.badgeFill)
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: systemImage)
                    .font(.system(size: size * 0.44, weight: .semibold))
                    .foregroundStyle(colorOverride ?? feature.stroke)
            )
    }
}

/// Thin progress bar — accent (or feature) track at ~13% with a solid fill.
struct GlassProgressBar: View {
    var value: Double // 0…1
    var tint: Color = .appPrimary
    var height: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(tint.opacity(0.13))
                Capsule().fill(tint)
                    .frame(width: max(0, min(1, value)) * geo.size.width)
            }
        }
        .frame(height: height)
    }
}
