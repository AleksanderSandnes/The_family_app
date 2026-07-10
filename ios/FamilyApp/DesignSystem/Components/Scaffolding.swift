// Screen scaffolding: FeatureTopBar, InputDialog, LifecycleResumeEffect.
import SwiftUI

extension View {
    /// Standard inline top bar for every detail/feature screen (back button comes free
    /// from NavigationStack). On iOS 26 the nav bar floats as frosted Liquid Glass, so
    /// content scrolls under it.
    func featureTopBar(_ title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }

    /// Calls `action` when the app returns to the foreground AND on first appear (refresh-on-screen-return).
    func resumeEffect(_ action: @escaping () -> Void) -> some View {
        modifier(ResumeEffectModifier(action: action))
    }
}

private struct ResumeEffectModifier: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    let action: () -> Void

    func body(content: Content) -> some View {
        content
            .onAppear(perform: action)
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active { action() }
            }
    }
}

/// Large screen header with trailing controls aligned to the title baseline — used by
/// tab-root screens (Family, Calendar) so the title and top-right buttons sit on one line.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: String, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.title = title
        self.trailing = trailing
    }

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            Text(title)
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(Color.appOnSurface)
            Spacer(minLength: Spacing.sm)
            trailing()
        }
        .padding(.vertical, Spacing.xs)
    }
}

/// Single or dual-field text-input alert.
/// Usage: `.inputDialog(isPresented:title:label:text:onConfirm:)`.
extension View {
    func inputDialog(
        isPresented: Binding<Bool>,
        title: String,
        label: String,
        text: Binding<String>,
        confirmLabel: String = "Save",
        secondLabel: String? = nil,
        secondText: Binding<String> = .constant(""),
        onConfirm: @escaping () -> Void
    ) -> some View {
        alert(title, isPresented: isPresented) {
            TextField(label, text: text)
            if let secondLabel {
                TextField(secondLabel, text: secondText)
            }
            Button(confirmLabel, action: onConfirm)
            Button("Cancel", role: .cancel) {}
        }
    }
}
