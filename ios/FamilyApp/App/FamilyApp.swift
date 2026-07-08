// @main entry point — the iOS twin of MainActivity + FamilyApp() in AppNavHost.kt:
// theme, auth gate (AuthFlow vs MainFlow) and auth deep links (familyapp://auth).
import SwiftUI
import UIKit

@main
struct FamilyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var root = RootViewModel()
    @State private var deepLinks = DeepLinkRouter()

    init() {
        ImagePipelineConfig.configure()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                switch root.gate {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.appBackground)
                case .signedOut:
                    AuthFlowView()
                case .needsPermissions:
                    PermissionsOnboardingScreen {
                        root.completePermissionsOnboarding()
                    }
                case .signedIn:
                    MainTabView()
                        .task { await root.onSignedIn() }
                }
            }
            .environment(root)
            .environment(deepLinks)
            .background(KeyboardDismissInstaller())
            .preferredColorScheme(SessionStore.shared.themeMode.colorScheme)
            // In-app language: drives every Text/LocalizedStringKey, live.
            .environment(\.locale, SessionStore.shared.appLanguage.locale)
            .onOpenURL { url in
                deepLinks.handle(url)
            }
            .onReceive(NotificationCenter.default.publisher(for: .openConversationDeepLink)) { note in
                // Push tap routed from the notification delegate.
                if let conversationId = note.userInfo?["conversationId"] as? String {
                    deepLinks.pendingConversationId = conversationId
                }
            }
            .task {
                await root.bootstrap()
            }
        }
    }
}

// MARK: - Tap-outside-to-dismiss keyboard (app-wide)

/// Installs a single tap recognizer on the key window so tapping anywhere outside the
/// keyboard/text field ends editing. `cancelsTouchesInView = false` plus a permissive
/// delegate mean it never swallows taps meant for buttons, list rows, or other controls.
struct KeyboardDismissInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        InstallerView()
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    private final class InstallerView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard let window else { return }
            let alreadyInstalled = window.gestureRecognizers?
                .contains { $0 is KeyboardDismissTapRecognizer } ?? false
            guard !alreadyInstalled else { return }
            let tap = KeyboardDismissTapRecognizer(
                target: KeyboardDismissCoordinator.shared,
                action: #selector(KeyboardDismissCoordinator.dismiss)
            )
            tap.cancelsTouchesInView = false
            tap.delegate = KeyboardDismissCoordinator.shared
            window.addGestureRecognizer(tap)
        }
    }
}

private final class KeyboardDismissTapRecognizer: UITapGestureRecognizer {}

private final class KeyboardDismissCoordinator: NSObject, UIGestureRecognizerDelegate {
    static let shared = KeyboardDismissCoordinator()

    @objc func dismiss() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
        )
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        // Ignore taps that land on a control or text input — let them work normally and
        // don't dismiss when the user is just moving between fields.
        var view = touch.view
        while let current = view {
            if current is UIControl || current is UITextView { return false }
            view = current.superview
        }
        return true
    }
}
