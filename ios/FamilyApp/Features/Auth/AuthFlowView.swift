// Placeholder — replaced by the full login/register flow in the auth phase.
import SwiftUI

struct AuthFlowView: View {
    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text("The Family App")
                .font(.headlineLarge)
                .foregroundStyle(Color.appOnBackground)
            Text("Sign-in arrives in the auth phase.")
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.appBackground)
    }
}
