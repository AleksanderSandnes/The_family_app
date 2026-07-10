// UserDefaults-backed session/preferences store. Keys are IDENTICAL to the Android DataStore
// keys so the mental model stays 1:1.
//
// CRITICAL dual-ID rule: `currentUserId` is the app's `public.users.id` (used in all
// foreign keys). It is NOT the Supabase Auth UUID (`auth_id`) — that one comes from
// `SupabaseClientProvider.client.auth.currentSession?.user.id` and is what RLS policies
// and Storage paths check. Mixing them up causes silent RLS denials.
import Foundation
import Observation

enum ThemeMode: String, CaseIterable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"
}

@Observable
@MainActor
final class SessionStore {
    static let shared = SessionStore()

    private let defaults: UserDefaults

    private enum Keys {
        static let userId = "current_user_id_v2"
        static let themeMode = "theme_mode"
        static let notificationsEnabled = "notifications_enabled"
        static let notifyDaysBefore = "notify_days_before"
        static let locationVisible = "location_visible"
        static let permissionsRequested = "permissions_requested"
        static let appLanguage = "app_language"
    }

    /// The app-internal `public.users.id` — NOT the auth UUID.
    private(set) var currentUserId: String?
    private(set) var themeMode: ThemeMode
    private(set) var notificationsEnabled: Bool
    private(set) var notifyDaysBefore: Int
    private(set) var locationVisible: Bool
    private(set) var permissionsRequested: Bool
    private(set) var appLanguage: AppLanguage

    /// Injectable defaults for tests; production uses the `shared` singleton.
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let storedId = defaults.string(forKey: Keys.userId)
        currentUserId = (storedId?.isEmpty == false) ? storedId : nil
        themeMode = ThemeMode(rawValue: defaults.string(forKey: Keys.themeMode) ?? "") ?? .system
        // object(forKey:) (not bool(forKey:)) so an unset value defaults to true, not false.
        notificationsEnabled = defaults.object(forKey: Keys.notificationsEnabled) as? Bool ?? true
        notifyDaysBefore = defaults.object(forKey: Keys.notifyDaysBefore) as? Int ?? 1
        locationVisible = defaults.bool(forKey: Keys.locationVisible)
        permissionsRequested = defaults.bool(forKey: Keys.permissionsRequested)
        appLanguage = AppLanguage(rawValue: defaults.string(forKey: Keys.appLanguage) ?? "") ?? .english
    }

    func signIn(userId: String) {
        currentUserId = userId
        defaults.set(userId, forKey: Keys.userId)
    }

    func signOut() {
        currentUserId = nil
        defaults.removeObject(forKey: Keys.userId)
    }

    func setThemeMode(_ mode: ThemeMode) {
        themeMode = mode
        defaults.set(mode.rawValue, forKey: Keys.themeMode)
    }

    func setNotificationsEnabled(_ enabled: Bool) {
        notificationsEnabled = enabled
        defaults.set(enabled, forKey: Keys.notificationsEnabled)
    }

    func setNotifyDaysBefore(_ days: Int) {
        notifyDaysBefore = days
        defaults.set(days, forKey: Keys.notifyDaysBefore)
    }

    func setLocationVisible(_ visible: Bool) {
        locationVisible = visible
        defaults.set(visible, forKey: Keys.locationVisible)
    }

    func setPermissionsRequested() {
        permissionsRequested = true
        defaults.set(true, forKey: Keys.permissionsRequested)
    }

    func setAppLanguage(_ language: AppLanguage) {
        appLanguage = language
        defaults.set(language.rawValue, forKey: Keys.appLanguage)
    }
}
