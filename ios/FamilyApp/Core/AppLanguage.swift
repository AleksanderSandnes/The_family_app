// In-app language selection (independent of the system language). The chosen
// language drives the root `\.locale` environment, so every `Text`/LocalizedStringKey
// re-localizes live, and it is the locale util/VM code passes to `String(localized:)`.
import Foundation

enum AppLanguage: String, CaseIterable, Identifiable {
    case english = "en"
    case norwegian = "nb"

    var id: String {
        rawValue
    }

    var locale: Locale {
        Locale(identifier: rawValue)
    }

    /// Endonym — shown in the picker in its own language.
    var displayName: String {
        switch self {
        case .english: "English"
        case .norwegian: "Norsk"
        }
    }
}
