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
                case .signedIn:
                    MainTabView()
                }
            }
            .environment(root)
            .environment(deepLinks)
            .preferredColorScheme(SessionStore.shared.themeMode.colorScheme)
            .onOpenURL { url in
                deepLinks.handle(url)
            }
            .task {
                await root.bootstrap()
            }
        }
    }
}
