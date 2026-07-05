// Placeholder tab shell — replaced by the full 5-tab NavigationStack shell in the
// shell/home phase.
import SwiftUI

struct MainTabView: View {
    var body: some View {
        VStack(spacing: Spacing.lg) {
            Text("Signed in")
                .font(.headlineMedium)
                .foregroundStyle(Color.appOnBackground)
            Text("The tab shell arrives in the shell/home phase.")
                .font(.bodyMedium)
                .foregroundStyle(Color.appOnSurfaceVariant)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.appBackground)
    }
}
