// In-app localization helpers. The app's language is user-selectable (Settings →
// Language) and independent of the system locale; it drives the root `\.locale`
// environment, so SwiftUI Text/Button/Label/navigationTitle STRING LITERALS already
// localize with no wrapping. Use `L(…)` only for String-typed UI contexts that don't
// go through the environment locale: custom component parameters, String-typed
// navigationTitle/accessibilityLabel, and ViewModel/util-built display strings.
//
// RULE: only ever localize UI CHROME. Never wrap user DATA — family/list/meal/wishlist
// names, chat messages, invite codes, emails — those must render exactly as entered.
//
// NOTE: `String(localized:locale:)` does NOT use its `locale:` argument to choose the
// `.strings` table — table selection follows the process/device language. To honour the
// in-app language regardless of the device language we look the key up in the matching
// `<lang>.lproj` bundle explicitly.
import Foundation

/// Resolve (and cache) the `.lproj` bundle for a locale's language so string lookup
/// follows the in-app language rather than the device language.
private final class LocalizedBundleCache: @unchecked Sendable {
    static let shared = LocalizedBundleCache()
    private let lock = NSLock()
    private var cache: [String: Bundle] = [:]

    func bundle(forLanguage lang: String) -> Bundle {
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache[lang] { return cached }
        let resolved: Bundle = if let path = Bundle.main.path(forResource: lang, ofType: "lproj"),
                                  let bundle = Bundle(path: path) {
            bundle
        } else {
            .main
        }
        cache[lang] = resolved
        return resolved
    }
}

/// The `.lproj` bundle that matches `locale`'s language (falls back to the main bundle).
func localizedBundle(for locale: Locale) -> Bundle {
    let lang = locale.language.languageCode?.identifier ?? "en"
    return LocalizedBundleCache.shared.bundle(forLanguage: lang)
}

/// Localize an (optionally interpolated) UI string against an explicit language.
/// Non-isolated so ViewModel/util helpers can localize off the main actor.
func L(_ key: String.LocalizationValue, locale: Locale) -> String {
    String(localized: key, bundle: localizedBundle(for: locale), locale: locale)
}

/// Localize a UI string against the in-app selected language.
@MainActor
func L(_ key: String.LocalizationValue) -> String {
    L(key, locale: SessionStore.shared.appLanguage.locale)
}

/// Localize a *runtime-determined* key (e.g. a stored relation value like "Mom") against an
/// explicit language. Unlike `L(_:)`, the key isn't a compile-time literal, so this routes
/// through `NSLocalizedString`, which accepts a `String` key. Returns the key unchanged when
/// no translation exists.
func L(dynamic key: String, locale: Locale) -> String {
    guard !key.isEmpty else { return key }
    return NSLocalizedString(key, bundle: localizedBundle(for: locale), comment: "")
}

/// Localize a runtime-determined key against the in-app selected language.
@MainActor
func L(dynamic key: String) -> String {
    L(dynamic: key, locale: SessionStore.shared.appLanguage.locale)
}

/// The Locale currently selected in-app — pass to date/number formatters so they
/// format in the chosen language too.
@MainActor
var appLocale: Locale {
    SessionStore.shared.appLanguage.locale
}
