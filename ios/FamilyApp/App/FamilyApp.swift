// @main entry point — the iOS twin of MainActivity + FamilyApp() in AppNavHost.kt:
// theme, auth gate (AuthFlow vs MainFlow) and auth deep links (familyapp://auth).
import SwiftUI

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
            .preferredColorScheme(SessionStore.shared.themeMode.colorScheme)
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
