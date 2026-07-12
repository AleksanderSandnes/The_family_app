# Forgot-Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Self-service password reset: the user requests a 6-digit code by email on the login screen, then types the code plus a new password into the app. Android first, iOS parity second, plus a dashboard runbook for the Resend SMTP upgrade.

**Architecture:** Two-step single screen in the auth flow (mirrors the existing 2-step RegisterScreen). Step 1 calls Supabase `resetPasswordForEmail`; step 2 calls `verifyEmailOtp(type = RECOVERY)` (which signs the user in) then `updateUser { password }` then the existing `completeSignInAfterConfirmation()`, which persists the app user id — the root auth gate flips automatically, no navigation needed on success. No deep links.

**Tech Stack:** supabase-kt 3.1.4 (Android), supabase-swift 2.x (iOS), Hilt + StateFlow (Android), `@Observable @MainActor` (iOS), mockk + JUnit4 (Android tests), XCTest + MockRepository (iOS tests).

**Spec:** `docs/superpowers/specs/2026-07-12-forgot-password-reset-design.md`

## Global Constraints

- Work on branch `feat/forgot-password-reset` (already exists; spec is committed there).
- Commit messages: conventional prefixes; **NEVER add Co-Authored-By lines** (user preference).
- All Gradle commands run from `android/`. **Do not run `assembleDebug` or full builds** — run only the targeted unit-test command given in each step (user runs full builds manually). Never run Gradle in parallel with anything else.
- Every user-facing Android string goes in BOTH `android/app/src/main/res/values/strings.xml` and `android/app/src/main/res/values-nb/strings.xml` (Norwegian Bokmål). Every user-facing iOS string goes in BOTH `ios/FamilyApp/Resources/en.lproj/Localizable.strings` and `ios/FamilyApp/Resources/nb.lproj/Localizable.strings`.
- iOS cannot be compiled on this Linux machine. Author Swift sources + tests only; keep APIs conservative and aligned with supabase-swift 2.x. The Mac compile happens later.
- Never commit directly to `master` or `test`. The final task merges `feat/forgot-password-reset` → `test` and pushes; `test` → `master` only when the user says so.
- supabase-kt APIs used: `auth.resetPasswordForEmail(email)`, `auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email, token)`, `auth.updateUser { password = ... }` (import `io.github.jan.supabase.auth.OtpType`).
- supabase-swift APIs used: `auth.resetPasswordForEmail(_:)`, `auth.verifyOTP(email:token:type: .recovery)`, `auth.update(user: UserAttributes(password:))`.

---

### Task 1: Android — repository methods, OTP error mapping, strings

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/data/FamilyRepository.kt` (after `signOut()`, ~line 345)
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt` (the `AUTH_ERROR_MESSAGES` table, ~line 163)
- Modify: `android/app/src/main/res/values/strings.xml` (~line 312, near `forgot_password`)
- Modify: `android/app/src/main/res/values-nb/strings.xml` (~line 313)
- Test: `android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt`

**Interfaces:**
- Produces: `suspend fun FamilyRepository.sendPasswordResetEmail(email: String): Result<Unit>` and `suspend fun FamilyRepository.confirmPasswordReset(email: String, code: String, newPassword: String): Result<String>` (returns app user id). Task 2's ViewModel calls both. New string ids: `R.string.that_code_is_wrong_or_expired`, `R.string.enter_the_6_digit_code` and the screen strings listed below (Task 3 consumes them).

- [ ] **Step 1: Write the failing test** — add to the `friendlyAuthError` section of `AuthViewModelTest.kt`:

```kotlin
@Test
fun `friendlyAuthError maps expired or invalid otp codes`() {
    assertEquals(
        R.string.that_code_is_wrong_or_expired,
        friendlyAuthError(RuntimeException("Token has expired or is invalid"), isLogin = true),
    )
    assertEquals(
        R.string.that_code_is_wrong_or_expired,
        friendlyAuthError(RuntimeException("otp_expired"), isLogin = true),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: FAIL — unresolved reference `that_code_is_wrong_or_expired`.

- [ ] **Step 3: Add the strings.** In `values/strings.xml`, directly after `forgot_password` (line 312):

```xml
<string name="reset_password">Reset password</string>
<string name="reset_password_subtitle">We\'ll email you a 6-digit code to reset your password</string>
<string name="send_code">Send code</string>
<string name="enter_code_and_new_password">If an account exists for %1$s, we\'ve emailed a 6-digit code. Enter it below with your new password.</string>
<string name="reset_code">6-digit code</string>
<string name="new_password">New password</string>
<string name="set_new_password">Set new password</string>
<string name="resend_code">Resend code</string>
<string name="resend_code_in_seconds">Resend code (%1$d s)</string>
<string name="enter_the_6_digit_code">Please enter the 6-digit code from the email.</string>
<string name="that_code_is_wrong_or_expired">That code is wrong or has expired. Request a new one.</string>
<string name="remembered_your_password">Remembered your password?</string>
```

In `values-nb/strings.xml`, directly after `forgot_password` (line 313):

```xml
<string name="reset_password">Tilbakestill passord</string>
<string name="reset_password_subtitle">Vi sender deg en 6-sifret kode på e-post for å tilbakestille passordet</string>
<string name="send_code">Send kode</string>
<string name="enter_code_and_new_password">Hvis det finnes en konto for %1$s, har vi sendt en 6-sifret kode på e-post. Skriv den inn nedenfor sammen med det nye passordet.</string>
<string name="reset_code">6-sifret kode</string>
<string name="new_password">Nytt passord</string>
<string name="set_new_password">Sett nytt passord</string>
<string name="resend_code">Send koden på nytt</string>
<string name="resend_code_in_seconds">Send på nytt (%1$d s)</string>
<string name="enter_the_6_digit_code">Skriv inn den 6-sifrede koden fra e-posten.</string>
<string name="that_code_is_wrong_or_expired">Koden er feil eller utløpt. Be om en ny.</string>
<string name="remembered_your_password">Husket du passordet?</string>
```

- [ ] **Step 4: Add the error-table row** in `AuthViewModel.kt`, inside `AUTH_ERROR_MESSAGES`, after the `weak_password` row:

```kotlin
listOf("otp_expired", "token has expired", "invalid token") to R.string.that_code_is_wrong_or_expired,
```

- [ ] **Step 5: Add the repository methods** in `FamilyRepository.kt` after `signOut()` (~line 345). Add `import io.github.jan.supabase.auth.OtpType` to the imports:

```kotlin
suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
    runCatching {
        SupabaseManager.client.auth.resetPasswordForEmail(email.trim().lowercase())
    }

/** Verifies the emailed 6-digit recovery code (which signs the user in), sets the new
 *  password, and finalizes the app session so the auth gate flips. */
suspend fun confirmPasswordReset(
    email: String,
    code: String,
    newPassword: String,
): Result<String> =
    runCatching {
        val client = SupabaseManager.client
        client.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email.trim().lowercase(),
            token = code,
        )
        client.auth.updateUser { password = newPassword }
    }.mapCatching { completeSignInAfterConfirmation().getOrThrow() }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: PASS (all tests, including the new one).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/data/FamilyRepository.kt \
        android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/main/res/values-nb/strings.xml \
        android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): password-reset repository methods and OTP error mapping"
```

---

### Task 2: Android — AuthViewModel reset-flow state machine (TDD)

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt`
- Test: `android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt`

**Interfaces:**
- Consumes: `repo.sendPasswordResetEmail(email): Result<Unit>`, `repo.confirmPasswordReset(email, code, newPassword): Result<String>` (Task 1).
- Produces: `data class ResetUiState(step: Int = 1, email: String = "", loading: Boolean = false, @StringRes error: Int? = null, resendCooldownSeconds: Int = 0)`; on `AuthViewModel`: `val resetState: StateFlow<ResetUiState>`, `fun sendResetCode(email: String)`, `fun resendResetCode()`, `fun confirmPasswordReset(code: String, newPassword: String)`, `fun clearResetFlow()`, `internal const val RESET_CODE_LENGTH = 6`. Task 3's screen consumes all of these.

- [ ] **Step 1: Write the failing tests** — add a new section at the bottom of `AuthViewModelTest.kt` (add imports `kotlinx.coroutines.test.advanceTimeBy` and `kotlinx.coroutines.test.runCurrent`):

```kotlin
// ─────────────────────────────────────────────────────────────────────────
// Password reset flow
// ─────────────────────────────────────────────────────────────────────────

@Test
fun `sendResetCode with invalid email sets error and does not call repo`() =
    runTest(dispatcherRule.dispatcher) {
        vm.sendResetCode("not-an-email")
        advanceUntilIdle()

        assertEquals(R.string.please_enter_a_valid_email_address, vm.resetState.value.error)
        assertEquals(1, vm.resetState.value.step)
        coVerify(exactly = 0) { repo.sendPasswordResetEmail(any()) }
    }

@Test
fun `sendResetCode success advances to step 2 and starts the resend cooldown`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail("alice@example.com") } returns Result.success(Unit)

        vm.sendResetCode("alice@example.com")
        runCurrent()

        assertEquals(2, vm.resetState.value.step)
        assertEquals("alice@example.com", vm.resetState.value.email)
        assertEquals(60, vm.resetState.value.resendCooldownSeconds)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, vm.resetState.value.resendCooldownSeconds)
    }

@Test
fun `sendResetCode failure maps to a friendly error`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail(any()) } returns
            Result.failure(RuntimeException("rate limit exceeded"))

        vm.sendResetCode("alice@example.com")
        advanceUntilIdle()

        assertEquals(R.string.too_many_attempts, vm.resetState.value.error)
        assertEquals(1, vm.resetState.value.step)
    }

@Test
fun `resendResetCode is blocked while the cooldown is active`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail(any()) } returns Result.success(Unit)

        vm.sendResetCode("alice@example.com")
        runCurrent()
        vm.resendResetCode()
        runCurrent()

        coVerify(exactly = 1) { repo.sendPasswordResetEmail(any()) }
    }

@Test
fun `confirmPasswordReset with a short code sets error and does not call repo`() =
    runTest(dispatcherRule.dispatcher) {
        vm.confirmPasswordReset("123", "password1")
        advanceUntilIdle()

        assertEquals(R.string.enter_the_6_digit_code, vm.resetState.value.error)
        coVerify(exactly = 0) { repo.confirmPasswordReset(any(), any(), any()) }
    }

@Test
fun `confirmPasswordReset with a short password sets error`() =
    runTest(dispatcherRule.dispatcher) {
        vm.confirmPasswordReset("123456", "123")
        advanceUntilIdle()

        assertEquals(R.string.password_must_be_at_least_6_characters, vm.resetState.value.error)
        coVerify(exactly = 0) { repo.confirmPasswordReset(any(), any(), any()) }
    }

@Test
fun `confirmPasswordReset uses the email captured in step 1`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail("alice@example.com") } returns Result.success(Unit)
        coEvery { repo.confirmPasswordReset("alice@example.com", "123456", "newpass1") } returns
            Result.success("uid")

        vm.sendResetCode("alice@example.com")
        runCurrent()
        vm.confirmPasswordReset("123456", "newpass1")
        runCurrent()

        assertNull(vm.resetState.value.error)
        coVerify(exactly = 1) { repo.confirmPasswordReset("alice@example.com", "123456", "newpass1") }
    }

@Test
fun `confirmPasswordReset failure maps the otp error and stops loading`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail(any()) } returns Result.success(Unit)
        coEvery { repo.confirmPasswordReset(any(), any(), any()) } returns
            Result.failure(RuntimeException("Token has expired or is invalid"))

        vm.sendResetCode("alice@example.com")
        runCurrent()
        vm.confirmPasswordReset("123456", "newpass1")
        advanceUntilIdle()

        assertEquals(R.string.that_code_is_wrong_or_expired, vm.resetState.value.error)
        assertFalse(vm.resetState.value.loading)
    }

@Test
fun `clearResetFlow resets state to defaults`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.sendPasswordResetEmail(any()) } returns Result.success(Unit)
        vm.sendResetCode("alice@example.com")
        runCurrent()

        vm.clearResetFlow()

        assertEquals(ResetUiState(), vm.resetState.value)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: FAIL — unresolved references `sendResetCode`, `resetState`, `ResetUiState`, etc.

- [ ] **Step 3: Implement in `AuthViewModel.kt`.** Add constants next to the existing ones at the top of the file:

```kotlin
internal const val RESET_CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60
```

Add the state class next to `AuthUiState`:

```kotlin
/** State for the two-step password-reset screen. Step 1 = email, step 2 = code + new password. */
data class ResetUiState(
    val step: Int = 1,
    val email: String = "",
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val resendCooldownSeconds: Int = 0,
)
```

Add imports `kotlinx.coroutines.Job` and `kotlinx.coroutines.delay`. Add inside the ViewModel class:

```kotlin
private val _resetState = MutableStateFlow(ResetUiState())
val resetState: StateFlow<ResetUiState> = _resetState.asStateFlow()
private var cooldownJob: Job? = null

fun sendResetCode(email: String) {
    val norm = email.trim()
    if (!isValidEmail(norm)) {
        _resetState.update { it.copy(error = R.string.please_enter_a_valid_email_address) }
        return
    }
    _resetState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
        repo
            .sendPasswordResetEmail(norm)
            .onSuccess {
                _resetState.update { it.copy(loading = false, step = 2, email = norm) }
                startResendCooldown()
            }
            .onFailure { e ->
                Log.e("Auth", "Password reset email failed", e)
                _resetState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
            }
    }
}

fun resendResetCode() {
    val current = _resetState.value
    if (current.resendCooldownSeconds > 0 || current.loading) return
    _resetState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
        repo
            .sendPasswordResetEmail(current.email)
            .onSuccess {
                _resetState.update { it.copy(loading = false) }
                startResendCooldown()
            }
            .onFailure { e ->
                _resetState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
            }
    }
}

fun confirmPasswordReset(
    code: String,
    newPassword: String,
) {
    if (code.trim().length != RESET_CODE_LENGTH) {
        _resetState.update { it.copy(error = R.string.enter_the_6_digit_code) }
        return
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
        _resetState.update { it.copy(error = R.string.password_must_be_at_least_6_characters) }
        return
    }
    _resetState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
        repo
            .confirmPasswordReset(_resetState.value.email, code.trim(), newPassword)
            .onFailure { e ->
                Log.e("Auth", "Password reset confirm failed", e)
                _resetState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
            }
        // On success the session is persisted (completeSignInAfterConfirmation), so the
        // root auth gate flips and unmounts this flow — keep the button spinning until then.
    }
}

fun clearResetFlow() {
    cooldownJob?.cancel()
    _resetState.value = ResetUiState()
}

private fun startResendCooldown() {
    cooldownJob?.cancel()
    cooldownJob =
        viewModelScope.launch {
            var remaining = RESEND_COOLDOWN_SECONDS
            while (remaining > 0) {
                _resetState.update { it.copy(resendCooldownSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _resetState.update { it.copy(resendCooldownSeconds = 0) }
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt \
        android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): password-reset state machine in AuthViewModel"
```

---

### Task 3: Android — ResetPasswordScreen, route, login wiring

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/navigation/Routes.kt` (add constant next to `LOGIN`, line 4)
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/navigation/AppNavHost.kt` (`AuthFlow`, lines 126–143)
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthScreens.kt` (LoginScreen lines 79–181; new composable after RegisterScreen)
- Modify: `android/app/src/main/res/values/strings.xml` and `values-nb/strings.xml` (remove the obsolete "coming soon" string)

**Interfaces:**
- Consumes: `viewModel.resetState`, `sendResetCode`, `resendResetCode`, `confirmPasswordReset`, `clearResetFlow` (Task 2); strings from Task 1; existing private composables in `AuthScreens.kt`: `AuthScaffold(title, subtitle) { }`, `StepIndicator(currentStep, totalSteps)`, `ErrorBanner(message)`, `PasswordStrengthBar(password)` (line 350), `FamilyTextField`, `PrimaryButton`, `AuthFooter(prompt, action, onClick)`.
- Produces: `Routes.RESET_PASSWORD = "reset_password"`; `ResetPasswordScreen(onBackToLogin, viewModel)`; `LoginScreen` gains an `onNavigateToReset: () -> Unit` parameter.

- [ ] **Step 1: Add the route.** In `Routes.kt` after `LOGIN`:

```kotlin
const val RESET_PASSWORD = "reset_password"
```

- [ ] **Step 2: Rewire LoginScreen.** In `AuthScreens.kt`:
  - Add parameter `onNavigateToReset: () -> Unit,` after `onNavigateToRegister`.
  - Delete `var showForgotDialog by remember { mutableStateOf(false) }` (line 88) and the entire `if (showForgotDialog) { AlertDialog(...) }` block (lines 92–106).
  - Change the "Forgot password?" `Text`'s `.clickable` (line 160) to:

```kotlin
.clickable {
    viewModel.clearError()
    onNavigateToReset()
}
```

  - Remove any imports that only served the deleted dialog (e.g. `AlertDialog`, `TextButton`) **only if** nothing else in the file uses them (check with grep before removing).

- [ ] **Step 3: Add ResetPasswordScreen** in `AuthScreens.kt`, after `RegisterScreen`:

```kotlin
@Composable
fun ResetPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val reset by viewModel.resetState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { viewModel.clearResetFlow() } }

    val (title, subtitle) =
        when (reset.step) {
            1 -> stringResource(R.string.reset_password) to stringResource(R.string.reset_password_subtitle)
            else ->
                stringResource(R.string.reset_password) to
                    stringResource(R.string.enter_code_and_new_password, reset.email)
        }
    AuthScaffold(title = title, subtitle = subtitle) {
        StepIndicator(currentStep = reset.step, totalSteps = 2)
        ErrorBanner(reset.error?.let { stringResource(it) })
        if (reset.step == 1) {
            FamilyTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email),
                leadingIcon = Icons.Outlined.Mail,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (email.isNotBlank() && !reset.loading) viewModel.sendResetCode(email)
                        },
                    ),
                enabled = !reset.loading,
            )
            PrimaryButton(
                text = stringResource(R.string.send_code),
                onClick = { viewModel.sendResetCode(email) },
                enabled = email.isNotBlank(),
                loading = reset.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FamilyTextField(
                value = code,
                onValueChange = { code = it },
                label = stringResource(R.string.reset_code),
                leadingIcon = Icons.Outlined.Lock,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                enabled = !reset.loading,
            )
            FamilyTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = stringResource(R.string.new_password),
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (code.isNotBlank() && newPassword.isNotBlank() && !reset.loading) {
                                viewModel.confirmPasswordReset(code, newPassword)
                            }
                        },
                    ),
                enabled = !reset.loading,
            )
            PasswordStrengthBar(newPassword)
            PrimaryButton(
                text = stringResource(R.string.set_new_password),
                onClick = { viewModel.confirmPasswordReset(code, newPassword) },
                enabled = code.isNotBlank() && newPassword.isNotBlank(),
                loading = reset.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (reset.resendCooldownSeconds > 0) {
                    stringResource(R.string.resend_code_in_seconds, reset.resendCooldownSeconds)
                } else {
                    stringResource(R.string.resend_code)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (reset.resendCooldownSeconds > 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(Radius.extraSmall))
                        .clickable(enabled = reset.resendCooldownSeconds == 0 && !reset.loading) {
                            viewModel.resendResetCode()
                        }
                        .padding(Spacing.xs),
            )
        }
        AuthFooter(
            prompt = stringResource(R.string.remembered_your_password),
            action = stringResource(R.string.sign_in),
            onClick = onBackToLogin,
        )
    }
}
```

Match the exact `FamilyTextField` parameter names used by `LoginScreen` (lines 117–150); adjust if a named parameter differs.

- [ ] **Step 4: Wire the route.** In `AppNavHost.kt`'s `AuthFlow`, update the LOGIN destination and add the new one:

```kotlin
composable(Routes.LOGIN) {
    LoginScreen(
        onAuthenticated = { /* RootViewModel reacts to session change */ },
        onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
        onNavigateToReset = { navController.navigate(Routes.RESET_PASSWORD) },
    )
}
composable(Routes.RESET_PASSWORD) {
    ResetPasswordScreen(onBackToLogin = { navController.popBackStack() })
}
```

- [ ] **Step 5: Remove the obsolete placeholder string** `password_reset_is_coming_soon_contact_support_at_support_familyapp_com` from BOTH `values/strings.xml` (line 317) and `values-nb/strings.xml` (line 318) — this change made it unused. Verify with:

Run: `grep -rn "password_reset_is_coming_soon" android/app/src/`
Expected: no matches.

- [ ] **Step 6: Verify compilation via the unit-test task** (compiles main sources; do not run assembleDebug):

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthScreens.kt \
        android/app/src/main/java/com/sandnes/familyapp/ui/navigation/Routes.kt \
        android/app/src/main/java/com/sandnes/familyapp/ui/navigation/AppNavHost.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/main/res/values-nb/strings.xml
git commit -m "feat(auth): two-step reset-password screen replaces the coming-soon dialog"
```

---

### Task 4: iOS — repository methods, protocol, mock

**Files:**
- Modify: `ios/FamilyApp/Core/FamilyRepository+Auth.swift`
- Modify: `ios/FamilyApp/Core/FamilyRepositoryProtocol.swift` (the `// Auth` section, ~line 40)
- Modify: `ios/FamilyAppTests/Support/MockRepository.swift`

**Interfaces:**
- Produces: protocol methods `func sendPasswordResetEmail(email: String) async throws` and `func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String`; mock inspection points `resetEmailCalls: [String]`, `confirmResetCalls: [(email: String, code: String, newPassword: String)]`, error injectors `sendResetError: Error?`, `confirmResetError: Error?`. Task 5's ViewModel and tests consume these.

- [ ] **Step 1: Add to `FamilyRepository+Auth.swift`** before `signOut()`:

```swift
func sendPasswordResetEmail(email: String) async throws {
    let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    try await client.auth.resetPasswordForEmail(norm)
}

/// Verifies the emailed 6-digit recovery code (which signs the user in), sets the new
/// password, and finalizes the app session so the auth gate flips.
@discardableResult
func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String {
    let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    try await client.auth.verifyOTP(email: norm, token: code, type: .recovery)
    try await client.auth.update(user: UserAttributes(password: newPassword))
    return try await completeSignInAfterConfirmation()
}
```

- [ ] **Step 2: Add to `FamilyRepositoryProtocol.swift`** in the `// Auth` section after `signInWithGoogle()`:

```swift
func sendPasswordResetEmail(email: String) async throws
func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String
```

- [ ] **Step 3: Add to `MockRepository.swift`** — properties near the other `private(set)` records:

```swift
private(set) var resetEmailCalls: [String] = []
private(set) var confirmResetCalls: [(email: String, code: String, newPassword: String)] = []
var sendResetError: Error?
var confirmResetError: Error?
```

and functions near `login`:

```swift
func sendPasswordResetEmail(email: String) async throws {
    resetEmailCalls.append(email)
    if let sendResetError { throw sendResetError }
}

func confirmPasswordReset(email: String, code: String, newPassword: String) async throws -> String {
    confirmResetCalls.append((email: email, code: code, newPassword: newPassword))
    if let confirmResetError { throw confirmResetError }
    return confirmResult
}
```

- [ ] **Step 4: Sanity-check by inspection** (no Swift compiler on this machine): confirm the protocol, mock, and repository signatures match character-for-character. Grep:

Run: `grep -rn "sendPasswordResetEmail\|confirmPasswordReset" ios/`
Expected: identical signatures in all three files (plus VM/tests after Task 5).

- [ ] **Step 5: Commit**

```bash
git add ios/FamilyApp/Core/FamilyRepository+Auth.swift \
        ios/FamilyApp/Core/FamilyRepositoryProtocol.swift \
        ios/FamilyAppTests/Support/MockRepository.swift
git commit -m "feat(ios/auth): password-reset repository methods behind the protocol seam"
```

---

### Task 5: iOS — AuthViewModel reset flow + tests

**Files:**
- Modify: `ios/FamilyApp/Features/Auth/AuthViewModel.swift`
- Test: `ios/FamilyAppTests/AuthViewModelTests.swift`

**Interfaces:**
- Consumes: Task 4's protocol methods and mock inspection points; existing helpers `isValidEmail`, `friendlyAuthError`, `localized`, `setError`, `waitUntil` (test support).
- Produces: on `AuthViewModel`: `var resetStep: Int`, `var resetEmail: String`, `var resetCooldown: Int`, `func sendResetCode(email:)`, `func resendResetCode()`, `func confirmPasswordReset(code:newPassword:)`, `func clearResetFlow()`; global `let resetCodeLength = 6`. Task 6's view consumes these.

- [ ] **Step 1: Write the tests** — add to `AuthViewModelTests.swift`:

```swift
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
```

- [ ] **Step 2: Implement in `AuthViewModel.swift`.** Add next to `minPasswordLength`:

```swift
let resetCodeLength = 6
private let resendCooldownSeconds = 60
```

Add properties next to `loading`/`error`:

```swift
var resetStep = 1
var resetEmail = ""
var resetCooldown = 0
```

Add `private var cooldownTask: Task<Void, Never>?` next to `authListener`, and cancel it in `deinit` (`cooldownTask?.cancel()` alongside `authListener?.cancel()`).

Add methods after `signInWithGoogle()`:

```swift
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
```

Add the OTP row to `authErrorMessages` after the `weak_password` row:

```swift
(["otp_expired", "token has expired", "invalid token"], "That code is wrong or has expired. Request a new one."),
```

- [ ] **Step 3: Sanity-check by inspection** — reread the diff; confirm every symbol referenced by the tests exists with matching spelling. (Tests run on the Mac later.)

- [ ] **Step 4: Commit**

```bash
git add ios/FamilyApp/Features/Auth/AuthViewModel.swift ios/FamilyAppTests/AuthViewModelTests.swift
git commit -m "feat(ios/auth): password-reset flow in AuthViewModel with tests"
```

---

### Task 6: iOS — ResetPasswordScreen, wiring, localization

**Files:**
- Modify: `ios/FamilyApp/Features/Auth/AuthFlowView.swift`
- Modify: `ios/FamilyApp/Resources/en.lproj/Localizable.strings`
- Modify: `ios/FamilyApp/Resources/nb.lproj/Localizable.strings`

**Interfaces:**
- Consumes: Task 5's ViewModel API; existing views in `AuthFlowView.swift`: `AuthScaffold`, `StepIndicator(currentStep:totalSteps:)`, `PasswordStrengthBar` (line 323 — match the exact initializer used by RegisterScreen), `FamilyTextField`, `PrimaryButton`, `AuthFooter`, `ErrorBanner`, binding helper `.clearingError(viewModel)`.

- [ ] **Step 1: Rewire AuthFlowView and LoginScreen.** Replace the body of `AuthFlowView` with:

```swift
struct AuthFlowView: View {
    @State private var viewModel = AuthViewModel()
    @State private var showRegister = false
    @State private var showReset = false

    var body: some View {
        NavigationStack {
            LoginScreen(
                viewModel: viewModel,
                onNavigateToRegister: { showRegister = true },
                onNavigateToReset: { showReset = true }
            )
            .navigationDestination(isPresented: $showRegister) {
                RegisterScreen(viewModel: viewModel)
                    .navigationBarBackButtonHidden()
            }
            .navigationDestination(isPresented: $showReset) {
                ResetPasswordScreen(viewModel: viewModel)
                    .navigationBarBackButtonHidden()
            }
        }
    }
}
```

In `LoginScreen`: add `let onNavigateToReset: () -> Void` after `onNavigateToRegister`; delete `@State private var showForgotDialog = false` and the `.alert("Forgot password?", ...)` modifier (lines 78–82); change the forgot button (line 54) to:

```swift
Button(L("Forgot password?")) {
    viewModel.clearError()
    onNavigateToReset()
}
```

(keep its existing font/style modifiers). Note the callsite in `AuthFlowView` no longer uses the trailing-closure form — both closures are now labeled arguments.

- [ ] **Step 2: Add ResetPasswordScreen** after `LoginScreen` in `AuthFlowView.swift`:

```swift
// MARK: - Reset password (2 steps)

struct ResetPasswordScreen: View {
    @Bindable var viewModel: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var code = ""
    @State private var newPassword = ""

    var body: some View {
        AuthScaffold(
            title: L("Reset password"),
            subtitle: viewModel.resetStep == 1
                ? L("We'll email you a 6-digit code to reset your password.")
                : L("If an account exists for \(viewModel.resetEmail), we've emailed a 6-digit code. Enter it below with your new password.")
        ) {
            StepIndicator(currentStep: viewModel.resetStep, totalSteps: 2)
            ErrorBanner(message: viewModel.error)
            if viewModel.resetStep == 1 {
                FamilyTextField(
                    label: L("Email"),
                    text: $email.clearingError(viewModel),
                    systemImage: "envelope",
                    keyboardType: .emailAddress,
                    textContentType: .emailAddress,
                    autocapitalization: .never,
                    whiteField: true
                )
                PrimaryButton(
                    text: L("Send code"),
                    enabled: !email.isEmpty,
                    loading: viewModel.loading
                ) {
                    viewModel.sendResetCode(email: email)
                }
            } else {
                FamilyTextField(
                    label: L("6-digit code"),
                    text: $code.clearingError(viewModel),
                    systemImage: "key",
                    keyboardType: .numberPad,
                    whiteField: true
                )
                FamilyTextField(
                    label: L("New password"),
                    text: $newPassword.clearingError(viewModel),
                    systemImage: "lock",
                    isPassword: true,
                    textContentType: .newPassword,
                    whiteField: true
                )
                PasswordStrengthBar(password: newPassword)
                PrimaryButton(
                    text: L("Set new password"),
                    enabled: !code.isEmpty && !newPassword.isEmpty,
                    loading: viewModel.loading
                ) {
                    viewModel.confirmPasswordReset(code: code, newPassword: newPassword)
                }
                Button(
                    viewModel.resetCooldown > 0
                        ? L("Resend code (\(viewModel.resetCooldown) s)")
                        : L("Resend code")
                ) {
                    viewModel.resendResetCode()
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(viewModel.resetCooldown > 0 ? Color.secondary : Color.appPrimary)
                .disabled(viewModel.resetCooldown > 0 || viewModel.loading)
                .frame(maxWidth: .infinity)
            }
            AuthFooter(prompt: L("Remembered your password?"), action: L("Sign in")) {
                viewModel.clearResetFlow()
                dismiss()
            }
        }
        .onDisappear { viewModel.clearResetFlow() }
    }
}
```

Match `FamilyTextField` / `PasswordStrengthBar` parameter labels exactly to their definitions in this file (`PasswordStrengthBar` is at ~line 323 — copy the initializer style used by `RegisterScreen`). If `FamilyTextField` has no `textContentType: .newPassword` case precedent, plain `.password` is fine.

- [ ] **Step 3: Add localization entries.** In `en.lproj/Localizable.strings` (key = value in English):

```
"Reset password" = "Reset password";
"We'll email you a 6-digit code to reset your password." = "We'll email you a 6-digit code to reset your password.";
"Send code" = "Send code";
"If an account exists for %@, we've emailed a 6-digit code. Enter it below with your new password." = "If an account exists for %@, we've emailed a 6-digit code. Enter it below with your new password.";
"6-digit code" = "6-digit code";
"New password" = "New password";
"Set new password" = "Set new password";
"Resend code" = "Resend code";
"Resend code (%lld s)" = "Resend code (%lld s)";
"Remembered your password?" = "Remembered your password?";
"Please enter the 6-digit code from the email." = "Please enter the 6-digit code from the email.";
"That code is wrong or has expired. Request a new one." = "That code is wrong or has expired. Request a new one.";
```

In `nb.lproj/Localizable.strings`:

```
"Reset password" = "Tilbakestill passord";
"We'll email you a 6-digit code to reset your password." = "Vi sender deg en 6-sifret kode på e-post for å tilbakestille passordet.";
"Send code" = "Send kode";
"If an account exists for %@, we've emailed a 6-digit code. Enter it below with your new password." = "Hvis det finnes en konto for %@, har vi sendt en 6-sifret kode på e-post. Skriv den inn nedenfor sammen med det nye passordet.";
"6-digit code" = "6-sifret kode";
"New password" = "Nytt passord";
"Set new password" = "Sett nytt passord";
"Resend code" = "Send koden på nytt";
"Resend code (%lld s)" = "Send på nytt (%lld s)";
"Remembered your password?" = "Husket du passordet?";
"Please enter the 6-digit code from the email." = "Skriv inn den 6-sifrede koden fra e-posten.";
"That code is wrong or has expired. Request a new one." = "Koden er feil eller utløpt. Be om en ny.";
```

Match the interpolation format actually produced by `String.LocalizationValue` for `Int` (`%lld`) — check how existing counted strings in these files are written and copy that convention. Remove the old `"Password reset is coming soon. Contact support at support@familyapp.com"` entry from both files if present (this change made it unused).

- [ ] **Step 4: Verify by inspection + grep**

Run: `grep -rn "coming soon" ios/; grep -c "Resend code" ios/FamilyApp/Resources/en.lproj/Localizable.strings`
Expected: no "coming soon" matches; 2 Resend entries.

- [ ] **Step 5: Commit**

```bash
git add ios/FamilyApp/Features/Auth/AuthFlowView.swift \
        ios/FamilyApp/Resources/en.lproj/Localizable.strings \
        ios/FamilyApp/Resources/nb.lproj/Localizable.strings
git commit -m "feat(ios/auth): two-step reset-password screen replaces the coming-soon alert"
```

---

### Task 7: Runbook, vault update, merge to test

**Files:**
- Create: `obsidian/06_Notes_and_References/Password Reset & Resend Email Setup.md`
- Modify: `obsidian/05_Implementation_Plan/Implementation Plan.md` (add/mark the milestone)
- Modify: `obsidian/00 Home.md` (current status line)

**Interfaces:** none — documentation and git plumbing.

- [ ] **Step 1: Write the runbook** at `obsidian/06_Notes_and_References/Password Reset & Resend Email Setup.md`:

```markdown
# Password Reset & Resend Email Setup

The app-side reset flow (6-digit code) shipped in `feat/forgot-password-reset`.
Delivery starts on Supabase's built-in sender and upgrades to Resend via SMTP —
no app changes needed at any point.

## 1. REQUIRED NOW — put the code in the reset email

Supabase Dashboard → Authentication → Emails → **Reset Password** template.
The default template only contains a link; the app flow needs the 6-digit code.

- Subject: `Your Family App reset code`
- Body (replace the default):

    <h2>Reset your password</h2>
    <p>Enter this code in The Family App to choose a new password:</p>
    <h1 style="letter-spacing: 4px;">{{ .Token }}</h1>
    <p>The code expires in 1 hour. If you didn't ask for this, you can ignore this email.</p>

Note: OTP expiry is configurable under Authentication → Providers → Email
(default 3600 s — fine as is).

## 2. Domain — DONE

**thefamilyapp.app** — bought 2026-07-12 through Vercel ($9.99 first year).
DNS is managed at Vercel (Dashboard → Domains → thefamilyapp.app, or `vercel dns`).

## 3. Verify the domain in Resend

Resend Dashboard → Domains → Add Domain → `thefamilyapp.app`.
Resend shows the DNS records to add (SPF TXT + DKIM CNAMEs, optionally the bounce MX).
Add them at Vercel: Dashboard → Domains → thefamilyapp.app → DNS Records, or
`vercel dns add thefamilyapp.app <name> <TYPE> <value>` — or paste them to Claude,
who can add them via the Vercel CLI. Wait for status **Verified** (minutes to a few hours).
Then Resend → API Keys → create a key with sending access. Copy it once.

## 4. Point Supabase at Resend

Supabase Dashboard → Project Settings → Authentication → SMTP (enable custom SMTP):

| Field | Value |
|---|---|
| Host | `smtp.resend.com` |
| Port | `465` |
| Username | `resend` |
| Password | *the Resend API key* |
| Sender email | `auth@thefamilyapp.app` |
| Sender name | `The Family App` |

## 5. Raise the email rate limit

Dashboard → Authentication → Rate Limits → increase "emails per hour"
(the built-in default is very low; with Resend's free tier the ceiling is
100 emails/day, 3 000/month — set e.g. 30/hour).

## 6. Smoke test

From the app: Login → Forgot password? → enter a real account email → code
arrives → set a new password → app signs in. Also confirm the old password
no longer works.
```

- [ ] **Step 2: Update the vault status** — in `obsidian/05_Implementation_Plan/Implementation Plan.md` add (or mark ✅ if a row was added earlier) a "Forgot-password reset (Android + iOS)" milestone dated 2026-07-12, and refresh the current-status line in `obsidian/00 Home.md` to mention the reset flow shipped and the Resend runbook pending user action.

- [ ] **Step 3: Commit the vault + runbook**

```bash
git add obsidian/
git commit -m "docs(vault): password-reset milestone + Resend/domain runbook"
```

- [ ] **Step 4: Run the full Android unit-test suite once** (regression gate; still no assembleDebug):

Run: `cd android && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no failing tests.

- [ ] **Step 5: Merge to test and push** (NOT master — that happens only when the user says so):

```bash
git checkout test
git merge --no-edit feat/forgot-password-reset
git push origin feat/forgot-password-reset test
git checkout feat/forgot-password-reset
```

---

## Verification (whole feature)

1. All Android unit tests green (`./gradlew testDebugUnitTest`).
2. `grep` shows no leftover "coming soon" strings on either platform.
3. iOS sources compile later on the Mac (`xcodegen generate && xcodebuild test`), per the iOS workflow.
4. End-to-end smoke test on a device **after** the user edits the Reset Password email template (runbook step 1) — the code cannot arrive before that.
