package com.example.mainactivity.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_PASSWORD_LENGTH = 6

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
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
                repo.signInWithGoogle().onFailure { e ->
                    _state.update { it.copy(loading = false, error = friendlyAuthError(e, isLogin = true)) }
                }
            }
        }

        fun clearError() = _state.update { it.copy(error = null) }

        fun setError(message: String) = _state.update { it.copy(loading = false, error = message) }

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
                form.name.isBlank() -> return setError("Please enter your name.")
                !validate(email = form.email, password = form.password) -> return
                form.password != form.confirm -> return setError("Passwords do not match.")
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

        private fun validate(
            email: String,
            password: String,
        ): Boolean {
            if (!android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email.trim())
                    .matches()
            ) {
                setError("Please enter a valid email address.")
                return false
            }
            if (password.length < MIN_PASSWORD_LENGTH) {
                setError("Password must be at least 6 characters.")
                return false
            }
            return true
        }
    }

// Ordered keyword → message table. The first entry whose keywords appear in the raw
// error message wins. Keeps friendlyAuthError simple (data-driven, not a giant when).
private val AUTH_ERROR_MESSAGES: List<Pair<List<String>, String>> =
    listOf(
        listOf("invalid login credentials", "invalid_credentials") to "Incorrect email or password.",
        listOf("user already registered", "already been registered") to "An account with this email already exists.",
        listOf("email address is invalid") to "Please enter a valid email address.",
        listOf("password should be at least", "weak_password") to "Password must be at least 6 characters.",
        listOf("rate limit", "too many requests") to "Too many attempts. Please wait a moment and try again.",
        listOf("network", "unable to resolve", "connect") to "Network error. Please check your connection.",
    )

private fun friendlyAuthError(
    e: Throwable,
    isLogin: Boolean,
): String {
    val raw = e.message?.lowercase() ?: return "Something went wrong. Please try again."
    if ("redirect" in raw && "not allowed" in raw) return "Something went wrong. Please try again."
    AUTH_ERROR_MESSAGES.firstOrNull { (keywords, _) -> keywords.any { it in raw } }?.let { return it.second }
    return if (isLogin) "Sign in failed. Please try again." else "Registration failed. Please try again."
}
