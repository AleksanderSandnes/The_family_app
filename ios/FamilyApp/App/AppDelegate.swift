// UIApplicationDelegate — reserved for FirebaseMessaging registration (push phase).
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // FirebaseApp.configure() + Messaging delegate land here in the push phase.
        true
    }
}
