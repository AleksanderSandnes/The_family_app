// FamilyRepository — settings mirrors. Extracted from FamilyRepository.swift to keep the
// main type's body under the length limit. Theme/location/permission passthroughs plus the
// server-side mirror of notification prefs. Behaviour is identical.
import Foundation
import Supabase

extension FamilyRepository {
    func setThemeMode(_ mode: ThemeMode) {
        session.setThemeMode(mode)
    }

    func setNotificationsEnabled(_ enabled: Bool) async {
        session.setNotificationsEnabled(enabled)
        await updateUserNotificationPrefs(["notifications_enabled": .bool(enabled)])
    }

    func setNotifyDaysBefore(_ days: Int) async {
        session.setNotifyDaysBefore(days)
        await updateUserNotificationPrefs(["notify_days_before": .integer(days)])
    }

    func setLocationVisible(_ visible: Bool) {
        session.setLocationVisible(visible)
    }

    func setPermissionsRequested() {
        session.setPermissionsRequested()
    }

    /// Mirrors the client notification settings onto the user's row so the server-side
    /// daily-reminders function can honour them. UserDefaults stays the UI source of truth.
    private func updateUserNotificationPrefs(_ values: [String: AnyJSON]) async {
        guard let userId = session.currentUserId, !values.isEmpty else { return }
        do {
            try await client.from("users").update(values).eq("id", value: userId).execute()
            invalidateUserCache()
        } catch {
            // Best-effort mirror, same as Android's runCatching.
        }
    }

    /// Pushes the current stored notification settings to the server once after sign-in.
    func syncNotificationPrefsToServer() async {
        await updateUserNotificationPrefs([
            "notifications_enabled": .bool(session.notificationsEnabled),
            "notify_days_before": .integer(session.notifyDaysBefore),
        ])
    }
}
