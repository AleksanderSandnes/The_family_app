// Auth view model — the iOS twin of AuthViewModel.kt: login, 2-step registration,
// Google OAuth, friendly error mapping. Success flips the auth gate implicitly because
// completeSignInAfterConfirmation() persists the app user id in SessionStore.
import Foundation
import Observation
import Supabase

let minPasswordLength = 6
private let strongPasswordLength = 8

/// The sign-up form fields, grouped to keep register() to a single parameter.
struct RegistrationForm {
    var name = ""
    var email = ""
    var password = ""
    var confirm = ""
    var birthday = ""
    var mobile = ""
}

@Observable
@MainActor
final class AuthViewModel {
    var loading = false
    var error: String?

    private let repo: FamilyRepositoryProtocol
    private var authListener: Task<Void, Never>?

    init(repo: FamilyRepositoryProtocol = FamilyRepository.shared) {
        self.repo = repo
        // Finalize external (Google OAuth) sign-in: once the redirect lands and the
        // session becomes authenticated, resolve + persist the app user so the gate flips.
        authListener = Task {
            for await (event, _) in SupabaseClientProvider.client.auth.authStateChanges
                where event == .signedIn {
                _ = try? await FamilyRepository.shared.completeSignInAfterConfirmation()
            }
        }
    }

    isolated deinit {
        authListener?.cancel()
    }

    func clearError() {
        error = nil
    }

    func setError(_ message: String) {
        loading = false
        error = localized(message)
    }

    /// Localizes a runtime message whose English text doubles as its `.strings` key,
    /// so validation + mapped auth errors surface in the in-app language.
    private func localized(_ message: String) -> String {
        L(String.LocalizationValue(message), locale: appLocale)
    }

    func login(email: String, password: String) {
        guard validate(email: email, password: password) else { return }
        loading = true
        error = nil
        Task {
            do {
                try await repo.login(email: email, password: password)
                loading = false
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    func register(_ form: RegistrationForm) {
        if form.name.trimmingCharacters(in: .whitespaces).isEmpty {
            return setError("Please enter your name.")
        }
        guard validate(email: form.email, password: form.password) else { return }
        if form.password != form.confirm {
            return setError("Passwords do not match.")
        }
        loading = true
        error = nil
        Task {
            do {
                try await repo.register(
                    name: form.name,
                    email: form.email,
                    password: form.password,
                    birthday: form.birthday,
                    mobile: form.mobile
                )
                // Email confirmation is disabled — session is active immediately.
                try await repo.completeSignInAfterConfirmation()
                loading = false
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: false))
            }
        }
    }

    func signInWithGoogle() {
        loading = true
        error = nil
        Task {
            do {
                try await repo.signInWithGoogle()
                // The authStateChanges listener finalizes the app session.
                loading = false
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    private func validate(email: String, password: String) -> Bool {
        if !isValidEmail(email.trimmingCharacters(in: .whitespaces)) {
            setError("Please enter a valid email address.")
            return false
        }
        if password.count < minPasswordLength {
            setError("Password must be at least 6 characters.")
            return false
        }
        return true
    }
}

// MARK: - Pure helpers (unit-tested)

func isValidEmail(_ email: String) -> Bool {
    let pattern = #"^[A-Z0-9a-z._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$"#
    return email.range(of: pattern, options: .regularExpression) != nil
}

/// Password strength score 0–3 based on length and character variety — mirrors
/// passwordStrength in AuthScreens.kt.
func passwordStrength(_ password: String) -> Int {
    if password.count < minPasswordLength { return 0 }
    var score = 1
    if password.count >= strongPasswordLength { score += 1 }
    if password.contains(where: \.isUppercase), password.contains(where: \.isLowercase) {
        score += 1
    }
    if password.contains(where: { !$0.isLetter && !$0.isNumber }) { score += 1 }
    return min(score, 3)
}

/// Ordered keyword → message table; first match wins — mirrors AUTH_ERROR_MESSAGES.
let authErrorMessages: [(keywords: [String], message: String)] = [
    (["invalid login credentials", "invalid_credentials"], "Incorrect email or password."),
    (["user already registered", "already been registered"], "An account with this email already exists."),
    (["email address is invalid"], "Please enter a valid email address."),
    (["password should be at least", "weak_password"], "Password must be at least 6 characters."),
    (["rate limit", "too many requests"], "Too many attempts. Please wait a moment and try again."),
    (["network", "unable to resolve", "connect", "offline"], "Network error. Please check your connection."),
]

func friendlyAuthError(_ error: Error, isLogin: Bool) -> String {
    let raw = error.localizedDescription.lowercased()
    if raw.isEmpty { return "Something went wrong. Please try again." }
    if raw.contains("redirect"), raw.contains("not allowed") {
        return "Something went wrong. Please try again."
    }
    if let match = authErrorMessages.first(where: { entry in
        entry.keywords.contains(where: raw.contains)
    }) {
        return match.message
    }
    return isLogin ? "Sign in failed. Please try again." : "Registration failed. Please try again."
}
