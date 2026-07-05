// Screen scaffolding — mirrors Scaffolding.kt (FeatureTopBar, InputDialog) plus the
// LifecycleResumeEffect equivalent.
import SwiftUI

extension View {
    /// Standard top bar for every detail/feature screen — inline title on the
    /// navigation stack (back button comes free from NavigationStack). On iOS 26 the
    /// nav bar renders as system Liquid Glass; we let it float rather than forcing an
    /// opaque fill, so content scrolls under a frosted bar.
    func featureTopBar(_ title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }

    /// Calls `action` when the app returns to the foreground AND on first appear —
    /// the iOS twin of LifecycleResumeEffect (refresh-on-screen-return).
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

/// Single or dual-field text-input alert — mirrors InputDialog in Scaffolding.kt.
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
