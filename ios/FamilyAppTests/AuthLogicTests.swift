// Auth pure-logic tests — mirror the Android AuthViewModel validation/mapping rules.
import XCTest
@testable import FamilyApp

final class AuthLogicTests: XCTestCase {
    // MARK: passwordStrength (mirrors passwordStrength in AuthScreens.kt)

    func testPasswordStrengthTooShort() {
        XCTAssertEqual(passwordStrength(""), 0)
        XCTAssertEqual(passwordStrength("abc12"), 0) // 5 chars
    }

    func testPasswordStrengthWeak() {
        // 6-7 chars, one case, no symbol → 1
        XCTAssertEqual(passwordStrength("abcdef"), 1)
    }

    func testPasswordStrengthMedium() {
        // 8+ chars, single case, no symbols → 2
        XCTAssertEqual(passwordStrength("abcdefgh"), 2)
        // 6 chars with mixed case → 2
        XCTAssertEqual(passwordStrength("Abcdef"), 2)
    }

    func testPasswordStrengthStrong() {
        // 8+ chars + mixed case → 3
        XCTAssertEqual(passwordStrength("Abcdefgh"), 3)
        // caps at 3 even with symbols on top
        XCTAssertEqual(passwordStrength("Abcdefg1!"), 3)
        // 6 chars, mixed case + symbol → 3
        XCTAssertEqual(passwordStrength("Abcd1!"), 3)
    }

    // MARK: isValidEmail

    func testValidEmails() {
        XCTAssertTrue(isValidEmail("alice@example.com"))
        XCTAssertTrue(isValidEmail("a.b+tag@sub.domain.no"))
    }

    func testInvalidEmails() {
        XCTAssertFalse(isValidEmail(""))
        XCTAssertFalse(isValidEmail("alice"))
        XCTAssertFalse(isValidEmail("alice@"))
        XCTAssertFalse(isValidEmail("alice@example"))
        XCTAssertFalse(isValidEmail("@example.com"))
        XCTAssertFalse(isValidEmail("alice @example.com"))
    }

    // MARK: friendlyAuthError (mirrors AUTH_ERROR_MESSAGES)

    private struct FakeError: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }

    private func map(_ raw: String, isLogin: Bool = true) -> String {
        friendlyAuthError(FakeError(message: raw), isLogin: isLogin)
    }

    func testInvalidCredentialsMapping() {
        XCTAssertEqual(map("Invalid login credentials"), "Incorrect email or password.")
        XCTAssertEqual(map("error: invalid_credentials"), "Incorrect email or password.")
    }

    func testAlreadyRegisteredMapping() {
        XCTAssertEqual(
            map("User already registered", isLogin: false),
            "An account with this email already exists."
        )
    }

    func testWeakPasswordMapping() {
        XCTAssertEqual(
            map("Password should be at least 6 characters", isLogin: false),
            "Password must be at least 6 characters."
        )
    }

    func testRateLimitMapping() {
        XCTAssertEqual(
            map("Too many requests, rate limit exceeded"),
            "Too many attempts. Please wait a moment and try again."
        )
    }

    func testNetworkMapping() {
        XCTAssertEqual(map("A network error occurred"), "Network error. Please check your connection.")
    }

    func testRedirectNotAllowedIsGeneric() {
        XCTAssertEqual(
            map("redirect url not allowed by the server"),
            "Something went wrong. Please try again."
        )
    }

    func testFallbacksDifferByMode() {
        XCTAssertEqual(map("some unknown failure", isLogin: true), "Sign in failed. Please try again.")
        XCTAssertEqual(
            map("some unknown failure", isLogin: false),
            "Registration failed. Please try again."
        )
    }

    func testFirstMatchingEntryWins() {
        // Contains both "invalid login credentials" and "network" — the table is ordered,
        // so credentials wins.
        XCTAssertEqual(
            map("invalid login credentials over the network"),
            "Incorrect email or password."
        )
    }
}
