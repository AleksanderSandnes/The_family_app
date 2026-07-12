package com.sandnes.familyapp.ui.auth

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val MIN_PASSWORD_LENGTH = 6
private const val STRONG_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_SCORE = 3
internal const val RESET_CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60

data class AuthUiState(
    val loading: Boolean = false,
    // A string resource id so error copy resolves in the UI's current locale (NB support).
    @StringRes val error: Int? = null,
    val success: Boolean = false,
)

/** State for the two-step password-reset screen. Step 1 = email, step 2 = code + new password. */
data class ResetUiState(
    val step: Int = 1,
    val email: String = "",
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val resendCooldownSeconds: Int = 0,
)

/** The sign-up form fields, grouped to keep register() to a single parameter. */
data class RegistrationForm(
    val name: String,
    val email: String,
    val password: String,
    val confirm: String,
    val birthday: String,
    val mobile: String,
)

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AuthUiState())
        val state: StateFlow<AuthUiState> = _state.asStateFlow()

        init {
            // Finalize external (Google OAuth) sign-in: once the browser redirect lands and the
            // session becomes Authenticated, resolve + persist the app user so the auth gate flips.
            viewModelScope.launch {
                repo.sessionStatusFlow.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        repo.completeSignInAfterConfirmation()
                    }
                }
            }
        }

        fun signInWithGoogle() {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                repo
                    .signInWithGoogle()
                    .onFailure { e ->
                        _state.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
                    }
                    // Browser handoff succeeded. If the user cancels there, no callback ever
                    // arrives — don't leave the whole form disabled while waiting.
                    .onSuccess { _state.update { it.copy(loading = false) } }
            }
        }

        fun clearError() = _state.update { it.copy(error = null) }

        fun setError(
            @StringRes messageRes: Int,
        ) = _state.update { it.copy(loading = false, error = messageRes) }

        fun login(
            email: String,
            password: String,
        ) {
            if (!validate(email = email, password = password)) return
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val result = repo.login(email, password)
                _state.update {
                    result.fold(
                        onSuccess = { AuthUiState(success = true) },
                        onFailure = { e ->
                            Log.e("Auth", "Login failed", e)
                            it.copy(loading = false, error = friendlyAuthError(e, isLogin = true))
                        },
                    )
                }
            }
        }

        fun register(form: RegistrationForm) {
            when {
                form.name.isBlank() -> return setError(R.string.please_enter_your_name)
                !validate(email = form.email, password = form.password) -> return
                form.password != form.confirm -> return setError(R.string.passwords_do_not_match)
            }
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                val registerResult = repo.register(form.name, form.email, form.password, form.birthday, form.mobile)
                if (registerResult.isFailure) {
                    val e = registerResult.exceptionOrNull()!!
                    Log.e("Auth", "Register failed", e)
                    _state.update { AuthUiState(error = friendlyAuthError(e, isLogin = false)) }
                    return@launch
                }
                // Email confirmation is disabled — session is active immediately after signUpWith.
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
            }
        }

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

        private fun validate(
            email: String,
            password: String,
        ): Boolean {
            if (!isValidEmail(email.trim())) {
                setError(R.string.please_enter_a_valid_email_address)
                return false
            }
            if (password.length < MIN_PASSWORD_LENGTH) {
                setError(R.string.password_must_be_at_least_6_characters)
                return false
            }
            return true
        }
    }

// Pure, framework-free email check (mirrors iOS `isValidEmail`). Kept off `android.util.Patterns`
// so it runs — and is unit-testable — in a plain JVM test without Robolectric.
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

internal fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

/** Password strength score 0–3 based on length and character variety. Mirrors iOS. */
internal fun passwordStrength(password: String): Int {
    if (password.length < MIN_PASSWORD_LENGTH) return 0
    var score = 1 // at least 6 chars = minimum score of 1
    if (password.length >= STRONG_PASSWORD_LENGTH) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score.coerceIn(0, MAX_PASSWORD_SCORE)
}

// Ordered keyword → string-resource table. The first entry whose keywords appear in the raw
// error message wins. Resource ids (not strings) so the UI resolves them in the active locale.
private val AUTH_ERROR_MESSAGES: List<Pair<List<String>, Int>> =
    listOf(
        listOf("invalid login credentials", "invalid_credentials") to R.string.incorrect_email_or_password,
        listOf("user already registered", "already been registered") to R.string.an_account_with_this_email_already_exists,
        listOf("email address is invalid") to R.string.please_enter_a_valid_email_address,
        listOf("password should be at least", "weak_password") to R.string.password_must_be_at_least_6_characters,
        listOf("otp_expired", "token has expired", "invalid token") to R.string.that_code_is_wrong_or_expired,
        listOf("rate limit", "too many requests") to R.string.too_many_attempts,
        listOf("network", "unable to resolve", "connect") to R.string.network_error_check_connection,
    )

@StringRes
internal fun friendlyAuthError(
    e: Throwable,
    isLogin: Boolean,
): Int {
    val raw = e.message?.lowercase() ?: return R.string.something_went_wrong
    if ("redirect" in raw && "not allowed" in raw) return R.string.something_went_wrong
    AUTH_ERROR_MESSAGES.firstOrNull { (keywords, _) -> keywords.any { it in raw } }?.let { return it.second }
    return if (isLogin) R.string.sign_in_failed else R.string.registration_failed
}
