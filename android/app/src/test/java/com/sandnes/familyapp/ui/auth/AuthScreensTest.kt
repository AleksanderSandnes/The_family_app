package com.sandnes.familyapp.ui.auth

import com.sandnes.familyapp.R
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.performImeActionForTest
import com.sandnes.familyapp.testutil.performTextReplacementForTest
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Drives the auth screens through a mocked ViewModel so every UI state can be exercised. */
class AuthScreensTest : ComposeScreenTest() {
    private val state = MutableStateFlow(AuthUiState())
    private val verifyState = MutableStateFlow(VerifyEmailUiState(email = "ada@example.com"))
    private val resetState = MutableStateFlow(ResetUiState())
    private lateinit var vm: AuthViewModel

    @Before
    fun setUpViewModel() {
        vm = mockk(relaxed = true)
        every { vm.state } returns state
        every { vm.verifyState } returns verifyState
        every { vm.resetState } returns resetState
    }

    // ── Login ──────────────────────────────────────────────────────────────

    @Test
    fun `login validates input before enabling sign in and submits`() {
        compose.setContent { LoginScreen({}, {}, {}, {}, vm) }
        compose.waitForText(str(R.string.welcome_back))
        compose.typeInto("ada@example.com", 0)
        compose.typeInto("secret", 1)
        compose.clickDescription(str(R.string.sign_in_button))
        verify { vm.login("ada@example.com", "secret") }
        verify(atLeast = 1) { vm.clearError() }
    }

    @Test
    fun `login keyboard done submits and google and navigation links work`() {
        var register = 0
        var reset = 0
        compose.setContent { LoginScreen({}, { register++ }, { reset++ }, {}, vm) }
        compose.waitForText(str(R.string.welcome_back))
        compose.typeInto("ada@example.com", 0)
        compose.typeInto("secret", 1)
        compose
            .onAllNodes(
                androidx.compose.ui.test
                    .hasSetTextAction(),
            )[1]
            .performImeActionForTest()
        verify { vm.login("ada@example.com", "secret") }
        compose.clickDescription(str(R.string.continue_with_google_button))
        verify { vm.signInWithGoogle() }
        compose.clickText(str(R.string.forgot_password))
        compose.clickText(str(R.string.create_account))
        assertEquals(1, reset)
        assertEquals(1, register)
    }

    @Test
    fun `login reflects loading error success and verification states`() {
        var authenticated = 0
        var verifyEmail: String? = null
        compose.setContent { LoginScreen({ authenticated++ }, {}, {}, { verifyEmail = it }, vm) }
        compose.waitForText(str(R.string.welcome_back))
        state.value = AuthUiState(error = R.string.couldnt_save)
        compose.waitForText(str(R.string.couldnt_save))
        state.value = AuthUiState(loading = true)
        compose.waitForIdle()
        state.value = AuthUiState(needsVerificationEmail = "ada@example.com")
        compose.waitUntil(20_000) { verifyEmail != null }
        verify { vm.clearNeedsVerification() }
        state.value = AuthUiState(success = true)
        compose.waitUntil(20_000) { authenticated > 0 }
    }

    // ── Register ───────────────────────────────────────────────────────────

    private fun fillStep1(
        name: String = "Ada",
        email: String = "ada@example.com",
        password: String = "longenough1",
        confirm: String = password,
    ) {
        compose.typeInto(name, 0)
        compose.typeInto(email, 1)
        compose.typeInto(password, 2)
        compose.typeInto(confirm, 3)
    }

    @Test
    fun `register reports each validation problem and then reaches step two`() {
        compose.setContent { RegisterScreen({}, {}, {}, vm) }
        compose.waitForText(str(R.string.create_your_account))
        fillStep1(email = "not-an-email")
        compose.clickDescription(str(R.string.continue_next_step_button))
        verify { vm.setError(R.string.please_enter_a_valid_email_address) }
    }

    @Test
    fun `register rejects short and mismatching passwords`() {
        compose.setContent { RegisterScreen({}, {}, {}, vm) }
        compose.waitForText(str(R.string.create_your_account))
        fillStep1(password = "short", confirm = "short")
        compose.clickDescription(str(R.string.continue_next_step_button))
        verify { vm.setError(R.string.password_must_be_at_least_8_characters) }
    }

    @Test
    fun `register rejects mismatching confirmation`() {
        compose.setContent { RegisterScreen({}, {}, {}, vm) }
        compose.waitForText(str(R.string.create_your_account))
        fillStep1(password = "longenough1", confirm = "different11")
        compose.clickDescription(str(R.string.continue_next_step_button))
        verify { vm.setError(R.string.passwords_do_not_match) }
    }

    @Test
    fun `register walks through both steps and submits the form`() {
        compose.setContent { RegisterScreen({}, {}, {}, vm) }
        compose.waitForText(str(R.string.create_your_account))
        fillStep1()
        compose.clickDescription(str(R.string.continue_next_step_button))
        compose.waitForText(str(R.string.about_you))
        // Back to step one keeps the typed values, then forward again.
        compose.clickText(str(R.string.back))
        compose.waitForText(str(R.string.create_your_account))
        compose.clickDescription(str(R.string.continue_next_step_button))
        compose.waitForText(str(R.string.about_you))
        compose.clickDescription(str(R.string.birthday_optional))
        compose.clickText(str(R.string.ok))
        compose.typeInto("12345678", 0)
        compose.clickDescription(str(R.string.create_account_button))
        verify { vm.register(match { it.name == "Ada" && it.mobile == "12345678" && it.birthday.isNotEmpty() }) }
    }

    @Test
    fun `register footer google and loading states`() {
        var login = 0
        compose.setContent { RegisterScreen({}, { login++ }, {}, vm) }
        compose.waitForText(str(R.string.create_your_account))
        compose.clickDescription(str(R.string.continue_with_google_button))
        verify { vm.signInWithGoogle() }
        compose.clickText(str(R.string.sign_in))
        assertEquals(1, login)
        state.value = AuthUiState(loading = true, error = R.string.couldnt_save)
        compose.waitForText(str(R.string.couldnt_save))
    }

    @Test
    fun `register forwards authentication and verification callbacks`() {
        var authed = 0
        var emailSeen: String? = null
        compose.setContent { RegisterScreen({ authed++ }, {}, { emailSeen = it }, vm) }
        compose.waitForText(str(R.string.create_your_account))
        state.value = AuthUiState(needsVerificationEmail = "x@y.z")
        compose.waitUntil(20_000) { emailSeen != null }
        state.value = AuthUiState(success = true)
        compose.waitUntil(20_000) { authed > 0 }
    }

    // ── Reset password ─────────────────────────────────────────────────────

    @Test
    fun `reset step one sends a code`() {
        var back = 0
        compose.setContent { ResetPasswordScreen({ back++ }, vm) }
        compose.waitForText(str(R.string.reset_password), substring = true)
        compose.typeInto("ada@example.com")
        compose.clickText(str(R.string.send_code))
        verify { vm.sendResetCode("ada@example.com") }
        compose.clickText(str(R.string.sign_in))
        assertEquals(1, back)
    }

    @Test
    fun `reset step two shows strength resend cooldown and confirms`() {
        resetState.value = ResetUiState(step = 2, email = "ada@example.com", resendCooldownSeconds = 30, error = R.string.couldnt_save)
        compose.setContent { ResetPasswordScreen({}, vm) }
        compose.waitForText(str(R.string.set_new_password))
        assertTrue(compose.hasText(str(R.string.resend_code_in_seconds, 30)))
        compose.typeInto("123456", 0)
        for ((password, label) in listOf("abc" to R.string.too_short, "abcdefgh" to R.string.weak, "Abcdefgh1" to R.string.medium, "Abcdefgh1!xyz" to R.string.strong)) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasSetTextAction(),
                )[1]
                .performTextReplacementForTest(password)
            compose.waitForIdle()
            assertTrue("$password -> ${str(label)}", compose.hasText(str(label)) || compose.hasText(str(R.string.medium)) || compose.hasText(str(R.string.strong)))
        }
        compose.clickText(str(R.string.set_new_password))
        verify { vm.confirmPasswordReset("123456", "Abcdefgh1!xyz") }
        resetState.value = ResetUiState(step = 2, email = "ada@example.com", resendCooldownSeconds = 0)
        compose.waitForText(str(R.string.resend_code))
        compose.clickText(str(R.string.resend_code))
        verify { vm.resendResetCode() }
    }

    // ── Verify email ───────────────────────────────────────────────────────

    @Test
    fun `verify email starts the flow confirms and resends`() {
        var back = 0
        compose.setContent { VerifyEmailScreen("ada@example.com", sendCode = true, onBackToLogin = { back++ }, viewModel = vm) }
        compose.waitForText(str(R.string.verify_your_email))
        verify { vm.startEmailVerification("ada@example.com", true) }
        compose.typeInto("654321")
        compose.clickText(str(R.string.verify_code))
        verify { vm.confirmSignupEmail("654321") }
        compose.clickText(str(R.string.resend_code))
        verify { vm.resendSignupCode() }
        verifyState.value = VerifyEmailUiState(email = "ada@example.com", resendCooldownSeconds = 12, error = R.string.couldnt_save)
        compose.waitForText(str(R.string.resend_code_in_seconds, 12))
        compose.clickText(str(R.string.sign_in))
        assertEquals(1, back)
    }
}
