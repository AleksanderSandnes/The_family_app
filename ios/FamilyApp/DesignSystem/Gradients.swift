// Signature gradients — mirrors Color.kt.
// brandGradient is the ONLY sanctioned gradient and may appear only on identity
// surfaces: hero headers, outgoing chat bubbles, the brand primary CTA, and the
// Home family banner. Do not use as a generic card/background fill.
import SwiftUI

enum Gradients {
    static let brand = LinearGradient(
        colors: [Palette.indigo600, Palette.violet600],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let brandSoft = LinearGradient(
        colors: [Palette.indigo500, Palette.violet500],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static func hero(dark: Bool) -> LinearGradient {
        dark
            ? LinearGradient(
                colors: [Palette.heroDarkStart, Palette.heroDarkEnd],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            : brand
    }
}
