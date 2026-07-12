// Behaviour tests for AuthViewModel via MockRepository: login/register/google reach the
// repo with entered values, and validation short-circuits before any repo call.
@testable import FamilyApp
import XCTest

@MainActor
final class AuthViewModelTests: XCTestCase {
    private func makeVM(_ mock: MockRepository) -> AuthViewModel {
        AuthViewModel(repo: mock)
    }

    // MARK: - Login

    func testLoginCallsRepoWithEnteredValues() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.login(email: "alice@test.com", password: "secret1")
        await waitUntil { !mock.loginCalls.isEmpty }
        XCTAssertEqual(
            mock.loginCalls.first,
            MockRepository.LoginRecord(email: "alice@test.com", password: "secret1")
        )
    }

    func testLoginWithInvalidEmailDoesNotCallRepo() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.login(email: "not-an-email", password: "secret1")
        await waitUntil { vm.error != nil }
        XCTAssertTrue(mock.loginCalls.isEmpty)
    }

    // MARK: - Register

    func testRegisterCallsRepoWithEnteredValues() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        var form = RegistrationForm()
        form.name = "Bob"
        form.email = "bob@test.com"
        form.password = "secret1"
        form.confirm = "secret1"
        form.birthday = "1990-01-01"
        form.mobile = "12345678"

        vm.register(form)
        await waitUntil { !mock.registerCalls.isEmpty }
        let record = mock.registerCalls.first
        XCTAssertEqual(record?.name, "Bob")
        XCTAssertEqual(record?.email, "bob@test.com")
        XCTAssertEqual(record?.password, "secret1")
        XCTAssertEqual(record?.birthday, "1990-01-01")
        XCTAssertEqual(record?.mobile, "12345678")
    }

    func testRegisterPasswordMismatchDoesNotCallRepo() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        var form = RegistrationForm()
        form.name = "Bob"
        form.email = "bob@test.com"
        form.password = "secret1"
        form.confirm = "different"

        vm.register(form)
        await waitUntil { vm.error != nil }
        XCTAssertTrue(mock.registerCalls.isEmpty)
    }

    // MARK: - Password reset

    func testSendResetCodeAdvancesToStepTwoAndStartsCooldown() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.sendResetCode(email: "alice@test.com")
        await waitUntil { vm.resetStep == 2 }
        XCTAssertEqual(mock.resetEmailCalls, ["alice@test.com"])
        XCTAssertEqual(vm.resetEmail, "alice@test.com")
        XCTAssertTrue(vm.resetCooldown > 0)
    }

    func testSendResetCodeInvalidEmailDoesNotCallRepo() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.sendResetCode(email: "not-an-email")
        await waitUntil { vm.error != nil }
        XCTAssertTrue(mock.resetEmailCalls.isEmpty)
        XCTAssertEqual(vm.resetStep, 1)
    }

    func testResendResetCodeBlockedDuringCooldown() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.sendResetCode(email: "alice@test.com")
        await waitUntil { vm.resetStep == 2 }
        vm.resendResetCode()
        try? await Task.sleep(for: .milliseconds(100))
        XCTAssertEqual(mock.resetEmailCalls.count, 1)
    }

    func testConfirmPasswordResetValidatesCodeLength() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.confirmPasswordReset(code: "123", newPassword: "secret1")
        await waitUntil { vm.error != nil }
        XCTAssertTrue(mock.confirmResetCalls.isEmpty)
    }

    func testConfirmPasswordResetValidatesPasswordLength() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.confirmPasswordReset(code: "123456", newPassword: "123")
        await waitUntil { vm.error != nil }
        XCTAssertTrue(mock.confirmResetCalls.isEmpty)
    }

    func testConfirmPasswordResetUsesStoredEmail() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.sendResetCode(email: "alice@test.com")
        await waitUntil { vm.resetStep == 2 }
        vm.confirmPasswordReset(code: "123456", newPassword: "newpass1")
        await waitUntil { !mock.confirmResetCalls.isEmpty }
        XCTAssertEqual(mock.confirmResetCalls.first?.email, "alice@test.com")
        XCTAssertEqual(mock.confirmResetCalls.first?.code, "123456")
        XCTAssertEqual(mock.confirmResetCalls.first?.newPassword, "newpass1")
    }

    func testConfirmPasswordResetSurfacesOtpError() async {
        let mock = MockRepository()
        mock.confirmResetError = NSError(
            domain: "auth", code: 403,
            userInfo: [NSLocalizedDescriptionKey: "Token has expired or is invalid"]
        )
        let vm = makeVM(mock)
        vm.sendResetCode(email: "alice@test.com")
        await waitUntil { vm.resetStep == 2 }
        vm.confirmPasswordReset(code: "123456", newPassword: "newpass1")
        await waitUntil { vm.error != nil }
        XCTAssertFalse(vm.loading)
    }

    func testClearResetFlowResetsState() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.sendResetCode(email: "alice@test.com")
        await waitUntil { vm.resetStep == 2 }
        vm.clearResetFlow()
        XCTAssertEqual(vm.resetStep, 1)
        XCTAssertEqual(vm.resetEmail, "")
        XCTAssertEqual(vm.resetCooldown, 0)
    }

    // MARK: - Google

    func testSignInWithGoogleCallsRepo() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.signInWithGoogle()
        await waitUntil { mock.googleSignInCalled }
        XCTAssertTrue(mock.googleSignInCalled)
    }
}
