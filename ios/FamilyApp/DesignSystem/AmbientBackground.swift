// The ambient canvas (Liquid Glass 1c) — three soft radial washes (violet top-right,
// indigo left, teal bottom) over a near-white/near-black base. Every screen floats its
// glass surfaces above this. Reproduces the CSS radial-gradient stack from the spec.
import SwiftUI

struct AmbientBackground: View {
    @Environment(\.colorScheme) private var scheme

    private var dark: Bool {
        scheme == .dark
    }

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            ZStack {
                if dark {
                    Color(hex: 0x0B0D16)
                } else {
                    Color(hex: 0xEFF1F8)
                }

                // violet — top-right
                wash(
                    color: Color(hex: 0x7C3AED).opacity(dark ? 0.32 : 0.17),
                    center: CGPoint(x: 0.88 * width, y: 0.06 * height),
                    radius: 0.85 * width
                )
                // indigo — left
                wash(
                    color: Color(hex: 0x5457E8).opacity(dark ? 0.28 : 0.16),
                    center: CGPoint(x: -0.05 * width, y: 0.30 * height),
                    radius: 1.05 * width
                )
                // teal — bottom
                wash(
                    color: Color(hex: 0x14B8A6).opacity(dark ? 0.18 : 0.13),
                    center: CGPoint(x: 0.60 * width, y: 1.02 * height),
                    radius: 0.98 * width
                )
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
    }

    private func wash(color: Color, center: CGPoint, radius: CGFloat) -> some View {
        RadialGradient(
            colors: [color, color.opacity(0)],
            center: .center,
            startRadius: 0,
            endRadius: radius
        )
        .frame(width: radius * 2, height: radius * 2)
        .position(center)
        .blendMode(.plusLighter)
    }
}

extension View {
    /// Places the ambient wash behind this view and lets content scroll over it.
    func ambientBackground() -> some View {
        background(AmbientBackground())
    }
}
