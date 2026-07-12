# Signup Email Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** New email signups must enter a 6-digit emailed code before getting a session (and therefore before the permission screen). Unverified accounts that try to log in are routed into the same verification screen instead of a dead-end error.

**Architecture:** `enable_confirmations = true` makes `signUpWith` return no session; a shared `VerifyEmailScreen` (one per platform) collects the code and calls `verifyEmailOtp(type = SIGNUP)` → session → existing `completeSignInAfterConfirmation()` → auth gate flips to `NeedsPermissions`. Entry paths: register (code already sent by signup) and unverified login (screen auto-resends). `register()` checks session presence so the app is correct on both sides of the config flip.

**Tech Stack:** identical to the reset flow — supabase-kt 3.1.4, supabase-swift 2.x, Hilt/StateFlow, `@Observable @MainActor`, mockk/JUnit4, XCTest/MockRepository, `supabase config push`.

**Spec:** `docs/superpowers/specs/2026-07-12-signup-email-verification-design.md`

## Global Constraints

- Branch `feat/signup-email-verification` (created from master + spec).
- Same rules as the reset plan: NO Co-Authored-By lines; Gradle only `testDebugUnitTest` (no assembleDebug, never parallel); strings in EN + NB on both platforms; iOS authored only (no compile); never commit to master/test directly; merge feat → `test` at the end, master only on user request.
- `supabase config push` auto-applies with NO dry-run: the push task requires explicit user approval naming the changes, and config.toml must keep mirroring every managed value.
- APIs: supabase-kt `auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email, token)`, `auth.resendEmail(OtpType.Email.SIGNUP, email)`; supabase-swift `auth.verifyOTP(email:token:type: .signup)`, `auth.resend(email:type: .signup)`.
- Login "email not confirmed" detection keywords: `"email not confirmed"`, `"email_not_confirmed"`.

---

### Task 1: Backend — confirmation template + enable_confirmations flip

**Files:**
- Create: `supabase/templates/confirmation.html`
- Modify: `supabase/config.toml`

**Interfaces:**
- Produces: confirmation emails carry `{{ .Token }}`; email signups stop returning sessions. Everything after this task assumes confirmations are ON.

- [ ] **Step 1: Create `supabase/templates/confirmation.html`** — copy the structure of `supabase/templates/recovery.html` exactly (same wrapper div, table, badge, card, code chip, footer) with these content changes: `<h1>` text `Confirm your email`, intro `<p>` text `Welcome to The Family App! Enter this code in the app to verify your email:`, and the expiry line `The code expires in 1 hour. If you didn't create an account, you can safely ignore this email.`

- [ ] **Step 2: Update `supabase/config.toml`.** Change `enable_confirmations = false` to `true` and reword its comment to say the app handles verification via VerifyEmailScreen since this feature. Add after the recovery template block:

```toml
[auth.email.template.confirmation]
subject = "Your Family App verification code"
content_path = "./supabase/templates/confirmation.html"
```

- [ ] **Step 3: Push — REQUIRES USER APPROVAL.** Tell the user the push changes exactly two things: signup email confirmation turns ON (old app versions will error at signup until the update ships — pre-accepted) and the branded confirmation template. On approval:

Run: `bash -c 'source .envrc && supabase config push' 2>&1 | grep -vE "^\s*content" | sed 's/re_[A-Za-z0-9_]*/re_<redacted>/g'`
Expected: diff showing `enable_confirmations = true` + confirmation template; then re-run shows "Remote Auth config is up to date." (Ignore the trailing `LegacyConfigPushStorageReadNetworkError` — harmless read bug.)

- [ ] **Step 4: Verify the flip is live**

Run: `curl -s "https://bntcznvsbyshetndbxfa.supabase.co/auth/v1/settings" -H "apikey: <anon key from earlier in session or mcp get_publishable_keys>" | python3 -c "import json,sys; print('mailer_autoconfirm:', json.load(sys.stdin)['mailer_autoconfirm'])"`
Expected: `mailer_autoconfirm: False`

- [ ] **Step 5: Commit**

```bash
git add supabase/config.toml supabase/templates/confirmation.html
git commit -m "feat(auth-config): signup email confirmation ON + branded verification template"
```

---

### Task 2: Android — repository methods

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/data/FamilyRepository.kt` (after `confirmPasswordReset`)

**Interfaces:**
- Produces: `fun hasAuthSession(): Boolean`; `suspend fun confirmSignupEmail(email: String, code: String): Result<String>` (returns app user id); `suspend fun resendSignupCode(email: String): Result<Unit>`. Task 3 consumes all three.

- [ ] **Step 1: Add after `confirmPasswordReset`:**

```kotlin
fun hasAuthSession(): Boolean = SupabaseManager.client.auth.currentSessionOrNull() != null

/** Verifies the emailed 6-digit signup code (which signs the user in) and
 *  finalizes the app session so the auth gate flips. */
suspend fun confirmSignupEmail(
    email: String,
    code: String,
): Result<String> =
    runCatching {
        SupabaseManager.client.auth.verifyEmailOtp(
            type = OtpType.Email.SIGNUP,
            email = email.trim().lowercase(),
            token = code,
        )
    }.mapCatching { completeSignInAfterConfirmation().getOrThrow() }

suspend fun resendSignupCode(email: String): Result<Unit> =
    runCatching {
        SupabaseManager.client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim().lowercase())
    }
```

- [ ] **Step 2: Compile via the test task** (also proves `resendEmail`/`SIGNUP` exist in supabase-kt 3.1.4 — if the compiler disagrees on a name, check the installed sources and adjust, keeping semantics):

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/data/FamilyRepository.kt
git commit -m "feat(auth): signup verification repository methods"
```

---

### Task 3: Android — AuthViewModel verify flow (TDD)

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt`
- Test: `android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2's repo methods; existing `isValidEmail`, `friendlyAuthError`, `MIN_PASSWORD_LENGTH`, `RESET_CODE_LENGTH`, cooldown pattern.
- Produces: `data class VerifyEmailUiState(email: String = "", loading: Boolean = false, @StringRes error: Int? = null, resendCooldownSeconds: Int = 0)`; on `AuthViewModel`: `val verifyState: StateFlow<VerifyEmailUiState>`, `fun startEmailVerification(email: String, sendCode: Boolean)`, `fun confirmSignupEmail(code: String)`, `fun resendSignupCode()`, `fun clearVerifyFlow()`; `AuthUiState` gains `val needsVerificationEmail: String? = null` plus `fun clearNeedsVerification()`. Task 4's screens consume these.

- [ ] **Step 1: Write the failing tests** — new section in `AuthViewModelTest.kt`:

```kotlin
// ─────────────────────────────────────────────────────────────────────────
// Signup email verification
// ─────────────────────────────────────────────────────────────────────────

@Test
fun `register without a session routes to verification instead of completing sign-in`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.register(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        every { repo.hasAuthSession() } returns false

        vm.register(RegistrationForm("Alice", "alice@example.com", "password", "password", "", ""))
        advanceUntilIdle()

        assertEquals("alice@example.com", vm.state.value.needsVerificationEmail)
        assertFalse(vm.state.value.success)
        coVerify(exactly = 0) { repo.completeSignInAfterConfirmation() }
    }

@Test
fun `register with a session keeps the immediate sign-in path`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.register(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        every { repo.hasAuthSession() } returns true
        coEvery { repo.completeSignInAfterConfirmation() } returns Result.success("uid")

        vm.register(RegistrationForm("Alice", "alice@example.com", "password", "password", "", ""))
        advanceUntilIdle()

        assertTrue(vm.state.value.success)
        assertNull(vm.state.value.needsVerificationEmail)
    }

@Test
fun `login with unconfirmed email routes to verification instead of erroring`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.login(any(), any()) } returns
            Result.failure(RuntimeException("Email not confirmed"))

        vm.login("alice@example.com", "password")
        advanceUntilIdle()

        assertEquals("alice@example.com", vm.state.value.needsVerificationEmail)
        assertNull(vm.state.value.error)
    }

@Test
fun `startEmailVerification with sendCode resends and starts the cooldown`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.resendSignupCode("alice@example.com") } returns Result.success(Unit)

        vm.startEmailVerification("alice@example.com", sendCode = true)
        runCurrent()

        assertEquals("alice@example.com", vm.verifyState.value.email)
        assertEquals(60, vm.verifyState.value.resendCooldownSeconds)
        coVerify(exactly = 1) { repo.resendSignupCode("alice@example.com") }
    }

@Test
fun `startEmailVerification without sendCode only starts the cooldown`() =
    runTest(dispatcherRule.dispatcher) {
        vm.startEmailVerification("alice@example.com", sendCode = false)
        runCurrent()

        assertEquals(60, vm.verifyState.value.resendCooldownSeconds)
        coVerify(exactly = 0) { repo.resendSignupCode(any()) }
    }

@Test
fun `confirmSignupEmail with a short code sets error and does not call repo`() =
    runTest(dispatcherRule.dispatcher) {
        vm.confirmSignupEmail("123")
        advanceUntilIdle()

        assertEquals(R.string.enter_the_6_digit_code, vm.verifyState.value.error)
        coVerify(exactly = 0) { repo.confirmSignupEmail(any(), any()) }
    }

@Test
fun `confirmSignupEmail uses the captured email`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.confirmSignupEmail("alice@example.com", "123456") } returns Result.success("uid")

        vm.startEmailVerification("alice@example.com", sendCode = false)
        runCurrent()
        vm.confirmSignupEmail("123456")
        runCurrent()

        assertNull(vm.verifyState.value.error)
        coVerify(exactly = 1) { repo.confirmSignupEmail("alice@example.com", "123456") }
    }

@Test
fun `confirmSignupEmail failure maps the otp error and stops loading`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.confirmSignupEmail(any(), any()) } returns
            Result.failure(RuntimeException("Token has expired or is invalid"))

        vm.startEmailVerification("alice@example.com", sendCode = false)
        runCurrent()
        vm.confirmSignupEmail("123456")
        advanceUntilIdle()

        assertEquals(R.string.that_code_is_wrong_or_expired, vm.verifyState.value.error)
        assertFalse(vm.verifyState.value.loading)
    }

@Test
fun `resendSignupCode is blocked while the cooldown is active`() =
    runTest(dispatcherRule.dispatcher) {
        coEvery { repo.resendSignupCode(any()) } returns Result.success(Unit)

        vm.startEmailVerification("alice@example.com", sendCode = true)
        runCurrent()
        vm.resendSignupCode()
        runCurrent()

        coVerify(exactly = 1) { repo.resendSignupCode(any()) }
    }

@Test
fun `clearVerifyFlow resets verify state`() =
    runTest(dispatcherRule.dispatcher) {
        vm.startEmailVerification("alice@example.com", sendCode = false)
        runCurrent()

        vm.clearVerifyFlow()

        assertEquals(VerifyEmailUiState(), vm.verifyState.value)
    }
```

Note: `register` success tests elsewhere in the file now need `every { repo.hasAuthSession() } returns true` — the mock is `relaxed` so it returns false by default; update the existing `register success flips success flag after sign-in completion` test to stub it.

- [ ] **Step 2: Run to verify failure**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: FAIL — unresolved `verifyState`, `startEmailVerification`, `needsVerificationEmail`, etc.

- [ ] **Step 3: Implement in `AuthViewModel.kt`.**

`AuthUiState` becomes:

```kotlin
data class AuthUiState(
    val loading: Boolean = false,
    // A string resource id so error copy resolves in the UI's current locale (NB support).
    @StringRes val error: Int? = null,
    val success: Boolean = false,
    // Set when the account needs email verification (register without session,
    // or login rejected with "email not confirmed") — the screen navigates on it.
    val needsVerificationEmail: String? = null,
)
```

Add next to `ResetUiState`:

```kotlin
/** State for the signup email-verification screen (shared by register + unverified login). */
data class VerifyEmailUiState(
    val email: String = "",
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val resendCooldownSeconds: Int = 0,
)
```

In `register()`, replace the block after the failure return (`// Email confirmation is disabled…` through the end of the launch) with:

```kotlin
if (!repo.hasAuthSession()) {
    // Email confirmations are ON — no session until the emailed code is verified.
    _state.update { AuthUiState(needsVerificationEmail = form.email.trim().lowercase()) }
    return@launch
}
val signInResult = repo.completeSignInAfterConfirmation()
_state.update {
    signInResult.fold(
        onSuccess = { AuthUiState(success = true) },
        onFailure = { e ->
            Log.e("Auth", "Post-register sign-in failed", e)
            AuthUiState(error = friendlyAuthError(e, isLogin = false))
        },
    )
}
```

In `login()`, replace the `onFailure` mapping inside the `fold` with:

```kotlin
onFailure = { e ->
    Log.e("Auth", "Login failed", e)
    val raw = e.message?.lowercase() ?: ""
    if ("email not confirmed" in raw || "email_not_confirmed" in raw) {
        it.copy(loading = false, needsVerificationEmail = email.trim().lowercase())
    } else {
        it.copy(loading = false, error = friendlyAuthError(e, isLogin = true))
    }
},
```

Add `fun clearNeedsVerification() = _state.update { it.copy(needsVerificationEmail = null) }` next to `clearError()`.

Add the verify flow next to the reset flow (identical cooldown pattern, its own `verifyCooldownJob`):

```kotlin
private val _verifyState = MutableStateFlow(VerifyEmailUiState())
val verifyState: StateFlow<VerifyEmailUiState> = _verifyState.asStateFlow()
private var verifyCooldownJob: Job? = null

fun startEmailVerification(
    email: String,
    sendCode: Boolean,
) {
    _verifyState.value = VerifyEmailUiState(email = email)
    if (sendCode) {
        _verifyState.update { it.copy(loading = true) }
        viewModelScope.launch {
            repo
                .resendSignupCode(email)
                .onSuccess { _verifyState.update { it.copy(loading = false) } }
                .onFailure { e ->
                    _verifyState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
                }
            startVerifyCooldown()
        }
    } else {
        // The signup call just sent the code — only arm the cooldown.
        startVerifyCooldown()
    }
}

fun confirmSignupEmail(code: String) {
    if (code.trim().length != RESET_CODE_LENGTH) {
        _verifyState.update { it.copy(error = R.string.enter_the_6_digit_code) }
        return
    }
    _verifyState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
        repo
            .confirmSignupEmail(_verifyState.value.email, code.trim())
            .onFailure { e ->
                Log.e("Auth", "Signup verification failed", e)
                _verifyState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = false)) }
            }
        // On success the session is persisted — the auth gate flips to NeedsPermissions
        // and unmounts this flow; keep the button spinning until then.
    }
}

fun resendSignupCode() {
    val current = _verifyState.value
    if (current.resendCooldownSeconds > 0 || current.loading) return
    _verifyState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
        repo
            .resendSignupCode(current.email)
            .onSuccess {
                _verifyState.update { it.copy(loading = false) }
                startVerifyCooldown()
            }
            .onFailure { e ->
                _verifyState.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
            }
    }
}

fun clearVerifyFlow() {
    verifyCooldownJob?.cancel()
    _verifyState.value = VerifyEmailUiState()
}

private fun startVerifyCooldown() {
    verifyCooldownJob?.cancel()
    verifyCooldownJob =
        viewModelScope.launch {
            var remaining = RESEND_COOLDOWN_SECONDS
            while (remaining > 0) {
                _verifyState.update { it.copy(resendCooldownSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _verifyState.update { it.copy(resendCooldownSeconds = 0) }
        }
}
```

- [ ] **Step 4: Run tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: PASS (all, including the updated register test).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthViewModel.kt \
        android/app/src/test/java/com/sandnes/familyapp/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): signup verification state machine + unconfirmed-login routing"
```

---

### Task 4: Android — VerifyEmailScreen, route, wiring, strings

**Files:**
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/navigation/Routes.kt`
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/navigation/AppNavHost.kt` (AuthFlow)
- Modify: `android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthScreens.kt`
- Modify: `android/app/src/main/res/values/strings.xml` + `values-nb/strings.xml`

**Interfaces:**
- Consumes: Task 3's `verifyState`/actions and `needsVerificationEmail`; existing `AuthScaffold`, `ErrorBanner`, `FamilyTextField`, `PrimaryButton`, `AuthFooter`; reused strings `reset_code`, `resend_code`, `resend_code_in_seconds`, `already_have_an_account`, `sign_in`.
- Produces: `Routes.VERIFY_EMAIL = "verify_email/{email}?send={send}"` + `Routes.verifyEmail(email: String, send: Boolean)` builder; `VerifyEmailScreen(email, sendCode, onBackToLogin, viewModel)`.

- [ ] **Step 1: Routes.** Add to `Routes.kt`:

```kotlin
const val VERIFY_EMAIL = "verify_email/{email}?send={send}"

fun verifyEmail(email: String, send: Boolean) = "verify_email/${android.net.Uri.encode(email)}?send=$send"
```

(Put the import-free `android.net.Uri.encode` call inline as shown — `Routes` has no imports today; match file style.)

- [ ] **Step 2: Strings.** `values/strings.xml` after `remembered_your_password`:

```xml
<string name="verify_your_email">Verify your email</string>
<string name="enter_the_code_we_emailed_to">Enter the 6-digit code we emailed to %1$s.</string>
<string name="verify_code">Verify</string>
```

`values-nb/strings.xml` same position:

```xml
<string name="verify_your_email">Bekreft e-posten din</string>
<string name="enter_the_code_we_emailed_to">Skriv inn den 6-sifrede koden vi sendte til %1$s.</string>
<string name="verify_code">Bekreft</string>
```

- [ ] **Step 3: `VerifyEmailScreen`** in `AuthScreens.kt` after `ResetPasswordScreen` (single-step variant of its step-2 UI):

```kotlin
@Composable
fun VerifyEmailScreen(
    email: String,
    sendCode: Boolean,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val verify by viewModel.verifyState.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.startEmailVerification(email, sendCode) }
    DisposableEffect(Unit) { onDispose { viewModel.clearVerifyFlow() } }

    AuthScaffold(
        title = stringResource(R.string.verify_your_email),
        subtitle = stringResource(R.string.enter_the_code_we_emailed_to, email),
    ) {
        ErrorBanner(verify.error?.let { stringResource(it) })
        FamilyTextField(
            value = code,
            onValueChange = { code = it },
            label = stringResource(R.string.reset_code),
            leadingIcon = Icons.Outlined.Lock,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (code.isNotBlank() && !verify.loading) viewModel.confirmSignupEmail(code)
                    },
                ),
            enabled = !verify.loading,
        )
        PrimaryButton(
            text = stringResource(R.string.verify_code),
            onClick = { viewModel.confirmSignupEmail(code) },
            enabled = code.isNotBlank(),
            loading = verify.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (verify.resendCooldownSeconds > 0) {
                stringResource(R.string.resend_code_in_seconds, verify.resendCooldownSeconds)
            } else {
                stringResource(R.string.resend_code)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color =
                if (verify.resendCooldownSeconds > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(Radius.extraSmall))
                    .clickable(enabled = verify.resendCooldownSeconds == 0 && !verify.loading) {
                        viewModel.resendSignupCode()
                    }
                    .padding(Spacing.xs),
        )
        AuthFooter(
            prompt = stringResource(R.string.already_have_an_account),
            action = stringResource(R.string.sign_in),
            onClick = onBackToLogin,
        )
    }
}
```

- [ ] **Step 4: Navigation triggers.** In `LoginScreen` and `RegisterScreen`, add a parameter `onNeedsVerification: (String) -> Unit,` (after their existing navigation callbacks) and inside each, after the existing `LaunchedEffect(state.success)`:

```kotlin
LaunchedEffect(state.needsVerificationEmail) {
    state.needsVerificationEmail?.let {
        viewModel.clearNeedsVerification()
        onNeedsVerification(it)
    }
}
```

- [ ] **Step 5: AuthFlow wiring** in `AppNavHost.kt` (add `navArgument` imports if missing — `androidx.navigation.navArgument`, `androidx.navigation.NavType`):

```kotlin
composable(Routes.LOGIN) {
    LoginScreen(
        onAuthenticated = { /* RootViewModel reacts to session change */ },
        onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
        onNavigateToReset = { navController.navigate(Routes.RESET_PASSWORD) },
        onNeedsVerification = { email -> navController.navigate(Routes.verifyEmail(email, send = true)) },
    )
}
composable(Routes.REGISTER) {
    RegisterScreen(
        onAuthenticated = { },
        onNavigateToLogin = { navController.popBackStack() },
        onNeedsVerification = { email -> navController.navigate(Routes.verifyEmail(email, send = false)) },
    )
}
composable(
    Routes.VERIFY_EMAIL,
    arguments =
        listOf(
            navArgument("email") { type = NavType.StringType },
            navArgument("send") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
) { entry ->
    VerifyEmailScreen(
        email = entry.arguments?.getString("email").orEmpty(),
        sendCode = entry.arguments?.getBoolean("send") ?: false,
        onBackToLogin = { navController.popBackStack(Routes.LOGIN, inclusive = false) },
    )
}
```

Also add `import com.sandnes.familyapp.ui.auth.VerifyEmailScreen`.

- [ ] **Step 6: Compile + tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.sandnes.familyapp.ui.auth.AuthViewModelTest"`
Expected: BUILD SUCCESSFUL, PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/sandnes/familyapp/ui/auth/AuthScreens.kt \
        android/app/src/main/java/com/sandnes/familyapp/ui/navigation/Routes.kt \
        android/app/src/main/java/com/sandnes/familyapp/ui/navigation/AppNavHost.kt \
        android/app/src/main/res/values/strings.xml \
        android/app/src/main/res/values-nb/strings.xml
git commit -m "feat(auth): verify-email screen wired from register and unconfirmed login"
```

---

### Task 5: iOS — repository, protocol, mock

**Files:**
- Modify: `ios/FamilyApp/Core/FamilyRepository+Auth.swift`, `ios/FamilyApp/Core/FamilyRepositoryProtocol.swift`, `ios/FamilyAppTests/Support/MockRepository.swift`

**Interfaces:**
- Produces (protocol): `func hasAuthSession() -> Bool`, `func confirmSignupEmail(email: String, code: String) async throws -> String`, `func resendSignupCode(email: String) async throws`. Mock: `var hasSession = false`, `private(set) var confirmSignupCalls: [(email: String, code: String)]`, `private(set) var resendSignupCalls: [String]`, `var confirmSignupError: Error?`, `var resendSignupError: Error?`.

- [ ] **Step 1: `FamilyRepository+Auth.swift`** before `signOut()`:

```swift
func hasAuthSession() -> Bool {
    client.auth.currentSession != nil
}

/// Verifies the emailed 6-digit signup code (which signs the user in) and
/// finalizes the app session so the auth gate flips.
@discardableResult
func confirmSignupEmail(email: String, code: String) async throws -> String {
    let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    try await client.auth.verifyOTP(email: norm, token: code, type: .signup)
    return try await completeSignInAfterConfirmation()
}

func resendSignupCode(email: String) async throws {
    let norm = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    try await client.auth.resend(email: norm, type: .signup)
}
```

- [ ] **Step 2: Protocol** — add the three signatures in the `// Auth` section after `confirmPasswordReset`.

- [ ] **Step 3: Mock** — properties near the reset records and functions near `confirmPasswordReset`:

```swift
var hasSession = false
private(set) var confirmSignupCalls: [(email: String, code: String)] = []
private(set) var resendSignupCalls: [String] = []
var confirmSignupError: Error?
var resendSignupError: Error?
```

```swift
func hasAuthSession() -> Bool { hasSession }

func confirmSignupEmail(email: String, code: String) async throws -> String {
    confirmSignupCalls.append((email: email, code: code))
    if let confirmSignupError { throw confirmSignupError }
    return confirmResult
}

func resendSignupCode(email: String) async throws {
    resendSignupCalls.append(email)
    if let resendSignupError { throw resendSignupError }
}
```

- [ ] **Step 4: Grep-verify signatures match across the three files, commit**

```bash
git add ios/FamilyApp/Core/ ios/FamilyAppTests/Support/MockRepository.swift
git commit -m "feat(ios/auth): signup verification repository methods behind the protocol seam"
```

---

### Task 6: iOS — AuthViewModel verify flow + tests

**Files:**
- Modify: `ios/FamilyApp/Features/Auth/AuthViewModel.swift`
- Test: `ios/FamilyAppTests/AuthViewModelTests.swift`

**Interfaces:**
- Produces on `AuthViewModel`: `var needsVerificationEmail: String?`, `var verifyEmail = ""`, `var verifyCooldown = 0`, `func startEmailVerification(email:sendCode:)`, `func confirmSignupEmail(code:)`, `func resendSignupCode()`, `func clearVerifyFlow()`, `func clearNeedsVerification()`. Task 7's views consume these.

- [ ] **Step 1: Tests** — mirror the Android cases in `AuthViewModelTests.swift` (`waitUntil` pattern):

```swift
// MARK: - Signup email verification

func testRegisterWithoutSessionRoutesToVerification() async {
    let mock = MockRepository()
    mock.hasSession = false
    let vm = makeVM(mock)
    var form = RegistrationForm()
    form.name = "Bob"
    form.email = "bob@test.com"
    form.password = "secret1"
    form.confirm = "secret1"

    vm.register(form)
    await waitUntil { vm.needsVerificationEmail != nil }
    XCTAssertEqual(vm.needsVerificationEmail, "bob@test.com")
}

func testRegisterWithSessionKeepsImmediatePath() async {
    let mock = MockRepository()
    mock.hasSession = true
    let vm = makeVM(mock)
    var form = RegistrationForm()
    form.name = "Bob"
    form.email = "bob@test.com"
    form.password = "secret1"
    form.confirm = "secret1"

    vm.register(form)
    await waitUntil { !mock.registerCalls.isEmpty }
    try? await Task.sleep(for: .milliseconds(100))
    XCTAssertNil(vm.needsVerificationEmail)
}

func testUnconfirmedLoginRoutesToVerification() async {
    let mock = MockRepository()
    mock.loginError = NSError(
        domain: "auth", code: 400,
        userInfo: [NSLocalizedDescriptionKey: "Email not confirmed"]
    )
    let vm = makeVM(mock)
    vm.login(email: "bob@test.com", password: "secret1")
    await waitUntil { vm.needsVerificationEmail != nil }
    XCTAssertEqual(vm.needsVerificationEmail, "bob@test.com")
    XCTAssertNil(vm.error)
}

func testStartEmailVerificationWithSendCodeResends() async {
    let mock = MockRepository()
    let vm = makeVM(mock)
    vm.startEmailVerification(email: "bob@test.com", sendCode: true)
    await waitUntil { !mock.resendSignupCalls.isEmpty }
    XCTAssertEqual(vm.verifyEmail, "bob@test.com")
    XCTAssertTrue(vm.verifyCooldown > 0)
}

func testConfirmSignupEmailValidatesCodeLength() async {
    let mock = MockRepository()
    let vm = makeVM(mock)
    vm.confirmSignupEmail(code: "123")
    await waitUntil { vm.error != nil }
    XCTAssertTrue(mock.confirmSignupCalls.isEmpty)
}

func testConfirmSignupEmailUsesStoredEmail() async {
    let mock = MockRepository()
    let vm = makeVM(mock)
    vm.startEmailVerification(email: "bob@test.com", sendCode: false)
    vm.confirmSignupEmail(code: "123456")
    await waitUntil { !mock.confirmSignupCalls.isEmpty }
    XCTAssertEqual(mock.confirmSignupCalls.first?.email, "bob@test.com")
    XCTAssertEqual(mock.confirmSignupCalls.first?.code, "123456")
}
```

Requires the mock to gain `var loginError: Error?` thrown from `login` — add it in this task if `MockRepository.login` has no error injection yet:

```swift
var loginError: Error?
```

and inside `login(...)` after recording the call: `if let loginError { throw loginError }`.

- [ ] **Step 2: Implement** in `AuthViewModel.swift`. Properties next to the reset ones: `var needsVerificationEmail: String?`, `var verifyEmail = ""`, `var verifyCooldown = 0`, plus `private var verifyCooldownTask: Task<Void, Never>?` (cancel in `deinit`). Methods after the reset block:

```swift
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
```

`register()`: after the `try await repo.register(...)` call, replace the immediate `completeSignInAfterConfirmation` with:

```swift
if !repo.hasAuthSession() {
    // Email confirmations are ON — no session until the emailed code is verified.
    loading = false
    needsVerificationEmail = form.email.trimmingCharacters(in: .whitespaces).lowercased()
    return
}
_ = try await repo.completeSignInAfterConfirmation()
loading = false
```

`login()`: in the catch, before the friendly mapping:

```swift
let raw = error.localizedDescription.lowercased()
if raw.contains("email not confirmed") || raw.contains("email_not_confirmed") {
    loading = false
    needsVerificationEmail = email.trimmingCharacters(in: .whitespaces).lowercased()
    return
}
```

- [ ] **Step 3: Inspect diff for symbol consistency with the tests; commit**

```bash
git add ios/FamilyApp/Features/Auth/AuthViewModel.swift ios/FamilyAppTests/
git commit -m "feat(ios/auth): signup verification flow in AuthViewModel with tests"
```

---

### Task 7: iOS — VerifyEmailScreen, wiring, localization

**Files:**
- Modify: `ios/FamilyApp/Features/Auth/AuthFlowView.swift`
- Modify: `ios/FamilyApp/Resources/en.lproj/Localizable.strings` + `nb.lproj/Localizable.strings`

**Interfaces:**
- Consumes: Task 6's VM API; existing `AuthScaffold`, `ErrorBanner`, `FamilyTextField`, `PrimaryButton`, `AuthFooter`, `.clearingError`.

- [ ] **Step 1: AuthFlowView.** Add state + destination and route both entry paths through the shared VM:

```swift
@State private var showVerify = false
@State private var verifySendCode = false
```

```swift
.navigationDestination(isPresented: $showVerify) {
    VerifyEmailScreen(viewModel: viewModel, sendCode: verifySendCode)
        .navigationBarBackButtonHidden()
}
.onChange(of: viewModel.needsVerificationEmail) { _, email in
    guard email != nil else { return }
    // From register the code was just sent; from login we must resend.
    verifySendCode = showRegister == false
    showVerify = true
}
```

(Place the `.onChange` on the `LoginScreen` chain in `AuthFlowView`. When it fires from RegisterScreen the destination stacks on top — that's fine in a NavigationStack.)

- [ ] **Step 2: VerifyEmailScreen** after `ResetPasswordScreen`:

```swift
// MARK: - Verify email (signup)

struct VerifyEmailScreen: View {
    @Bindable var viewModel: AuthViewModel
    let sendCode: Bool
    @Environment(\.dismiss) private var dismiss

    @State private var code = ""

    var body: some View {
        AuthScaffold(
            title: L("Verify your email"),
            subtitle: L("Enter the 6-digit code we emailed to \(viewModel.needsVerificationEmail ?? viewModel.verifyEmail)."),
            showIcon: false
        ) {
            ErrorBanner(message: viewModel.error)
            FamilyTextField(
                label: L("6-digit code"),
                text: $code.clearingError(viewModel),
                systemImage: "key",
                keyboardType: .numberPad,
                whiteField: true
            )
            PrimaryButton(
                text: L("Verify"),
                enabled: !code.isEmpty,
                loading: viewModel.loading
            ) {
                viewModel.confirmSignupEmail(code: code)
            }
            Button(
                viewModel.verifyCooldown > 0
                    ? L("Resend code (\(viewModel.verifyCooldown) s)")
                    : L("Resend code")
            ) {
                viewModel.resendSignupCode()
            }
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(viewModel.verifyCooldown > 0 ? Color.secondary : Color.appPrimary)
            .disabled(viewModel.verifyCooldown > 0 || viewModel.loading)
            .frame(maxWidth: .infinity)
            AuthFooter(prompt: L("Already have an account?"), action: L("Sign in")) {
                viewModel.clearVerifyFlow()
                viewModel.clearNeedsVerification()
                dismiss()
            }
        }
        .onAppear {
            let email = viewModel.needsVerificationEmail ?? ""
            viewModel.clearNeedsVerification()
            viewModel.startEmailVerification(email: email, sendCode: sendCode)
        }
        .onDisappear { viewModel.clearVerifyFlow() }
    }
}
```

- [ ] **Step 3: Localization.** `en.lproj` (key = value):

```
"Verify your email" = "Verify your email";
"Enter the 6-digit code we emailed to %@." = "Enter the 6-digit code we emailed to %@.";
"Verify" = "Verify";
```

`nb.lproj`:

```
"Verify your email" = "Bekreft e-posten din";
"Enter the 6-digit code we emailed to %@." = "Skriv inn den 6-sifrede koden vi sendte til %@.";
"Verify" = "Bekreft";
```

("6-digit code", "Resend code", "Resend code (%lld s)", "Already have an account?", "Sign in" already exist.)

- [ ] **Step 4: Commit**

```bash
git add ios/FamilyApp/Features/Auth/AuthFlowView.swift ios/FamilyApp/Resources/
git commit -m "feat(ios/auth): verify-email screen wired from register and unconfirmed login"
```

---

### Task 8: Docs, full suite, merge to test

- [ ] **Step 1:** Vault: add the milestone to `obsidian/05_Implementation_Plan/Implementation Plan.md` (✅ Signup email verification, 2026-07-12) and a line in `obsidian/00 Home.md`; note in `Password Reset & Resend Email Setup.md` under "Next planned" that it shipped. Commit `docs(vault): signup email verification milestone`.
- [ ] **Step 2:** Full Android suite: `cd android && ./gradlew testDebugUnitTest` — expect green.
- [ ] **Step 3:** Merge + push (NOT master):

```bash
git checkout test && git merge --no-edit feat/signup-email-verification
git push origin feat/signup-email-verification test
git checkout feat/signup-email-verification
```

## Verification (whole feature)

1. Android unit suite green; iOS compiles on the Mac later.
2. Device smoke test (after user builds): fresh signup → branded code email from `auth@thefamilyapp.app` → verify → permission screen; unverified login → routed to code entry; wrong code → friendly error; resend works after cooldown.
3. `mailer_autoconfirm` false at `/auth/v1/settings`.
