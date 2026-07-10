package com.sandnes.familyapp.ui.auth

import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.util.MainDispatcherRule
import io.github.jan.supabase.auth.status.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [AuthViewModel] and its pure validation / error-mapping helpers.
 *
 * The VM delegates all IO to [FamilyRepository] (mocked with mockk). Email validation is a
 * framework-free regex ([isValidEmail]) so it runs in a plain JUnit4 environment — no Robolectric.
 * [FamilyRepository.sessionStatusFlow] is stubbed with a non-authenticated [SessionStatus] so the
 * init collector never triggers `completeSignInAfterConfirmation()`.
 */
@RunWith(JUnit4::class)
class AuthViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var sessionStatus: MutableStateFlow<SessionStatus>
    private lateinit var vm: AuthViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        sessionStatus = MutableStateFlow(SessionStatus.NotAuthenticated(isSignOut = false))
        every { repo.sessionStatusFlow } returns sessionStatus
        vm = AuthViewModel(repo)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure helpers: isValidEmail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `isValidEmail accepts a normal address`() {
        assertTrue(isValidEmail("alice@example.com"))
        assertTrue(isValidEmail("a.b+tag@sub.domain.co"))
    }

    @Test
    fun `isValidEmail rejects malformed addresses`() {
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("plainstring"))
        assertFalse(isValidEmail("no-at-sign.com"))
        assertFalse(isValidEmail("missing@tld"))
        assertFalse(isValidEmail("@example.com"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure helpers: passwordStrength
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `passwordStrength is zero below the minimum length`() {
        assertEquals(0, passwordStrength(""))
        assertEquals(0, passwordStrength("12345"))
    }

    @Test
    fun `passwordStrength scores length and variety`() {
        assertEquals(1, passwordStrength("abcdef")) // 6 chars, no variety
        assertEquals(2, passwordStrength("abcdefgh")) // 8 chars, still no variety
        assertEquals(3, passwordStrength("Abcdef1!")) // long + mixed case + symbol, capped at 3
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pure helpers: friendlyAuthError
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `friendlyAuthError maps known keywords`() {
        assertEquals(
            "Incorrect email or password.",
            friendlyAuthError(RuntimeException("Invalid login credentials"), isLogin = true),
        )
        assertEquals(
            "An account with this email already exists.",
            friendlyAuthError(RuntimeException("User already registered"), isLogin = false),
        )
        assertEquals(
            "Network error. Please check your connection.",
            friendlyAuthError(RuntimeException("Unable to resolve host"), isLogin = true),
        )
    }

    @Test
    fun `friendlyAuthError hides oauth redirect misconfiguration`() {
        assertEquals(
            "Something went wrong. Please try again.",
            friendlyAuthError(RuntimeException("redirect_to is not allowed"), isLogin = true),
        )
    }

    @Test
    fun `friendlyAuthError falls back per flow`() {
        assertEquals(
            "Sign in failed. Please try again.",
            friendlyAuthError(RuntimeException("weird unmapped error"), isLogin = true),
        )
        assertEquals(
            "Registration failed. Please try again.",
            friendlyAuthError(RuntimeException("weird unmapped error"), isLogin = false),
        )
    }

    @Test
    fun `friendlyAuthError handles a null message`() {
        assertEquals(
            "Something went wrong. Please try again.",
            friendlyAuthError(RuntimeException(), isLogin = true),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setError / clearError
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setError sets message and clears loading`() {
        vm.setError("Boom")
        assertEquals("Boom", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `clearError resets error to null`() {
        vm.setError("Boom")
        vm.clearError()
        assertNull(vm.state.value.error)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login — validation short-circuits before touching the repo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `login with invalid email sets error and does not call repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.login("not-an-email", "password")
            advanceUntilIdle()

            assertEquals("Please enter a valid email address.", vm.state.value.error)
            assertFalse(vm.state.value.success)
            coVerify(exactly = 0) { repo.login(any(), any()) }
        }

    @Test
    fun `login with short password sets error and does not call repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.login("alice@example.com", "123")
            advanceUntilIdle()

            assertEquals("Password must be at least 6 characters.", vm.state.value.error)
            coVerify(exactly = 0) { repo.login(any(), any()) }
        }

    @Test
    fun `login success flips success flag`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.login("alice@example.com", "password") } returns Result.success("uid")

            vm.login("alice@example.com", "password")
            advanceUntilIdle()

            assertTrue(vm.state.value.success)
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `login failure maps to a friendly error`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.login(any(), any()) } returns
                Result.failure(RuntimeException("Invalid login credentials"))

            vm.login("alice@example.com", "password")
            advanceUntilIdle()

            assertEquals("Incorrect email or password.", vm.state.value.error)
            assertFalse(vm.state.value.success)
            assertFalse(vm.state.value.loading)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // register — name / email / confirm validation and success path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `register with blank name sets error and does not call repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.register(RegistrationForm("  ", "alice@example.com", "password", "password", "", ""))
            advanceUntilIdle()

            assertEquals("Please enter your name.", vm.state.value.error)
            coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `register with invalid email sets error`() =
        runTest(dispatcherRule.dispatcher) {
            vm.register(RegistrationForm("Alice", "bad", "password", "password", "", ""))
            advanceUntilIdle()

            assertEquals("Please enter a valid email address.", vm.state.value.error)
            coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `register with mismatched confirmation sets error`() =
        runTest(dispatcherRule.dispatcher) {
            vm.register(RegistrationForm("Alice", "alice@example.com", "password", "different", "", ""))
            advanceUntilIdle()

            assertEquals("Passwords do not match.", vm.state.value.error)
            coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `register success flips success flag after sign-in completion`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.register("Alice", "alice@example.com", "password", "", "") } returns Result.success(Unit)
            coEvery { repo.completeSignInAfterConfirmation() } returns Result.success("uid")

            vm.register(RegistrationForm("Alice", "alice@example.com", "password", "password", "", ""))
            advanceUntilIdle()

            assertTrue(vm.state.value.success)
            assertNull(vm.state.value.error)
        }

    @Test
    fun `register failure maps to a friendly error`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.register(any(), any(), any(), any(), any()) } returns
                Result.failure(RuntimeException("User already registered"))

            vm.register(RegistrationForm("Alice", "alice@example.com", "password", "password", "", ""))
            advanceUntilIdle()

            assertEquals("An account with this email already exists.", vm.state.value.error)
            assertFalse(vm.state.value.success)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // signInWithGoogle — failure surfaces a friendly error
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `signInWithGoogle failure maps to a friendly error`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.signInWithGoogle() } returns Result.failure(RuntimeException("network down"))

            vm.signInWithGoogle()
            advanceUntilIdle()

            assertEquals("Network error. Please check your connection.", vm.state.value.error)
            assertFalse(vm.state.value.loading)
        }
}
