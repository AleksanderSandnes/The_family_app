@testable import FamilyApp

// SessionStore behavior: keys, defaults, and sign-in/out semantics.
import XCTest

@MainActor
final class SessionStoreTests: XCTestCase {
    private var defaults: UserDefaults!
    private let suiteName = "SessionStoreTests"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testDefaultsMatchAndroid() {
        let store = SessionStore(defaults: defaults)
        XCTAssertNil(store.currentUserId)
        XCTAssertEqual(store.themeMode, .system)
        XCTAssertTrue(store.notificationsEnabled)
        XCTAssertEqual(store.notifyDaysBefore, 1)
        XCTAssertFalse(store.locationVisible)
        XCTAssertFalse(store.permissionsRequested)
    }

    func testSignInPersistsUnderAndroidKey() {
        let store = SessionStore(defaults: defaults)
        store.signIn(userId: "user-123")
        XCTAssertEqual(store.currentUserId, "user-123")
        // The key matches the Android DataStore key.
        XCTAssertEqual(defaults.string(forKey: "current_user_id_v2"), "user-123")
    }

    func testSignOutClearsUserId() {
        let store = SessionStore(defaults: defaults)
        store.signIn(userId: "user-123")
        store.signOut()
        XCTAssertNil(store.currentUserId)
        XCTAssertNil(defaults.string(forKey: "current_user_id_v2"))
    }

    func testEmptyStoredIdReadsAsNil() {
        defaults.set("", forKey: "current_user_id_v2")
        let store = SessionStore(defaults: defaults)
        XCTAssertNil(store.currentUserId)
    }

    func testThemeModePersistsAndroidRawValues() {
        let store = SessionStore(defaults: defaults)
        store.setThemeMode(.dark)
        XCTAssertEqual(defaults.string(forKey: "theme_mode"), "DARK")
        let reloaded = SessionStore(defaults: defaults)
        XCTAssertEqual(reloaded.themeMode, .dark)
    }

    func testSettingsRoundTrip() {
        let store = SessionStore(defaults: defaults)
        store.setNotificationsEnabled(false)
        store.setNotifyDaysBefore(5)
        store.setLocationVisible(true)
        store.setPermissionsRequested()

        let reloaded = SessionStore(defaults: defaults)
        XCTAssertFalse(reloaded.notificationsEnabled)
        XCTAssertEqual(reloaded.notifyDaysBefore, 5)
        XCTAssertTrue(reloaded.locationVisible)
        XCTAssertTrue(reloaded.permissionsRequested)
    }
}
