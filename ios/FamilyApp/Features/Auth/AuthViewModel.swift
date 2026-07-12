// Auth view model: login, 2-step registration, Google OAuth, friendly error mapping.
// Success flips the auth gate implicitly because completeSignInAfterConfirmation()
// persists the app user id in SessionStore.
import Foundation
import Observation

let minPasswordLength = 6
private let strongPasswordLength = 8
let resetCodeLength = 6
private let resendCooldownSeconds = 60

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
    var resetStep = 1
    var resetEmail = ""
    var resetCooldown = 0
    var needsVerificationEmail: String?
    var verifyEmail = ""
    var verifyCooldown = 0

    private let repo: FamilyRepositoryProtocol
    private var authListener: Task<Void, Never>?
    private var cooldownTask: Task<Void, Never>?
    private var verifyCooldownTask: Task<Void, Never>?

    init(repo: FamilyRepositoryProtocol? = nil) {
        self.repo = repo ?? FamilyRepository.shared
        // Finalize external (Google OAuth) sign-in: once the redirect lands and the
        // session becomes authenticated, resolve + persist the app user so the gate flips.
        let repo = self.repo
        authListener = Task { [repo] in
            for await _ in repo.authSignedInEvents() {
                _ = try? await repo.completeSignInAfterConfirmation()
            }
        }
    }

    isolated deinit {
        authListener?.cancel()
        cooldownTask?.cancel()
        verifyCooldownTask?.cancel()
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
                _ = try await repo.login(email: email, password: password)
                loading = false
            } catch {
                loading = false
                let raw = error.localizedDescription.lowercased()
                if raw.contains("email not confirmed") || raw.contains("email_not_confirmed") {
                    needsVerificationEmail = email.trimmingCharacters(in: .whitespaces).lowercased()
                    return
                }
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
                if !repo.hasAuthSession() {
                    // Email confirmations are ON — no session until the emailed code is verified.
                    loading = false
                    needsVerificationEmail = form.email.trimmingCharacters(in: .whitespaces).lowercased()
                    return
                }
                _ = try await repo.completeSignInAfterConfirmation()
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

    func sendResetCode(email: String) {
        let norm = email.trimmingCharacters(in: .whitespaces)
        guard isValidEmail(norm) else {
            return setError("Please enter a valid email address.")
        }
        loading = true
        error = nil
        Task {
            do {
                try await repo.sendPasswordResetEmail(email: norm)
                loading = false
                resetEmail = norm
                resetStep = 2
                startResendCooldown()
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    func resendResetCode() {
        guard resetCooldown == 0, !loading else { return }
        loading = true
        error = nil
        Task {
            do {
                try await repo.sendPasswordResetEmail(email: resetEmail)
                loading = false
                startResendCooldown()
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    func confirmPasswordReset(code: String, newPassword: String) {
        let trimmed = code.trimmingCharacters(in: .whitespaces)
        guard trimmed.count == resetCodeLength else {
            return setError("Please enter the 6-digit code from the email.")
        }
        guard newPassword.count >= minPasswordLength else {
            return setError("Password must be at least 6 characters.")
        }
        loading = true
        error = nil
        Task {
            do {
                _ = try await repo.confirmPasswordReset(
                    email: resetEmail, code: trimmed, newPassword: newPassword
                )
                // Session persisted → RootViewModel flips the gate; keep the spinner until unmount.
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    func clearResetFlow() {
        cooldownTask?.cancel()
        resetStep = 1
        resetEmail = ""
        resetCooldown = 0
        error = nil
    }

    private func startResendCooldown() {
        cooldownTask?.cancel()
        cooldownTask = Task {
            resetCooldown = resendCooldownSeconds
            while resetCooldown > 0 {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
                resetCooldown -= 1
            }
        }
    }

    func clearNeedsVerification() {
        needsVerificationEmail = nil
    }

    func startEmailVerification(email: String, sendCode: Bool) {
        verifyEmail = email
        error = nil
        if sendCode {
            loading = true
            Task {
                do {
                    try await repo.resendSignupCode(email: email)
                    loading = false
                } catch {
                    loading = false
                    self.error = localized(friendlyAuthError(error, isLogin: true))
                }
                startVerifyCooldown()
            }
        } else {
            // The signup call just sent the code — only arm the cooldown.
            startVerifyCooldown()
        }
    }

    func confirmSignupEmail(code: String) {
        let trimmed = code.trimmingCharacters(in: .whitespaces)
        guard trimmed.count == resetCodeLength else {
            return setError("Please enter the 6-digit code from the email.")
        }
        loading = true
        error = nil
        Task {
            do {
                _ = try await repo.confirmSignupEmail(email: verifyEmail, code: trimmed)
                // Session persisted → RootViewModel flips the gate; keep the spinner until unmount.
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: false))
            }
        }
    }

    func resendSignupCode() {
        guard verifyCooldown == 0, !loading else { return }
        loading = true
        error = nil
        Task {
            do {
                try await repo.resendSignupCode(email: verifyEmail)
                loading = false
                startVerifyCooldown()
            } catch {
                loading = false
                self.error = localized(friendlyAuthError(error, isLogin: true))
            }
        }
    }

    func clearVerifyFlow() {
        verifyCooldownTask?.cancel()
        verifyEmail = ""
        verifyCooldown = 0
        error = nil
    }

    private func startVerifyCooldown() {
        verifyCooldownTask?.cancel()
        verifyCooldownTask = Task {
            verifyCooldown = resendCooldownSeconds
            while verifyCooldown > 0 {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
                verifyCooldown -= 1
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

/// Password strength score 0–3 based on length and character variety.
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

/// Ordered keyword → message table; first match wins.
let authErrorMessages: [(keywords: [String], message: String)] = [
    (["invalid login credentials", "invalid_credentials"], "Incorrect email or password."),
    (["user already registered", "already been registered"], "An account with this email already exists."),
    (["email address is invalid"], "Please enter a valid email address."),
    (["password should be at least", "weak_password"], "Password must be at least 6 characters."),
    (["otp_expired", "token has expired", "invalid token"], "That code is wrong or has expired. Request a new one."),
    (["rate limit", "too many requests"], "Too many attempts. Please wait a moment and try again."),
    (["network", "unable to resolve", "connect", "offline"], "Network error. Please check your connection."),
]

func friendlyAuthError(_ error: Error, isLogin: Bool) -> String {
    let raw = error.localizedDescription.lowercased()
    if raw.isEmpty { return "Something went wrong. Please try again." }
    // OAuth redirect-allowlist misconfiguration: hide the developer detail behind a generic message.
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
