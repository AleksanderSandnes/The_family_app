// Behaviour tests for AuthViewModel via the injected MockRepository (no live backend).
// The pure validators (isValidEmail / passwordStrength / friendlyAuthError) are covered in
// AuthLogicTests — these assert the login/register/google flows reach the repo with the
// entered values, and that validation short-circuits before any repo call.
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

    // MARK: - Google

    func testSignInWithGoogleCallsRepo() async {
        let mock = MockRepository()
        let vm = makeVM(mock)
        vm.signInWithGoogle()
        await waitUntil { mock.googleSignInCalled }
        XCTAssertTrue(mock.googleSignInCalled)
    }
}
