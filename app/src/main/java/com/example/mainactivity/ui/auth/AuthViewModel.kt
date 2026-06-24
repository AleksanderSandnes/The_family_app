package com.example.mainactivity.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class AuthViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

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

    fun register(
        name: String,
        email: String,
        password: String,
        confirm: String,
        birthday: String,
        mobile: String,
    ) {
        when {
            name.isBlank() -> return setError("Please enter your name.")
            !validate(email = email, password = password) -> return
            password != confirm -> return setError("Passwords do not match.")
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val registerResult = repo.register(name, email, password, birthday, mobile)
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
        if (password.length < 6) {
            setError("Password must be at least 6 characters.")
            return false
        }
        return true
    }
}

private fun friendlyAuthError(
    e: Throwable,
    isLogin: Boolean,
): String {
    val raw = e.message?.lowercase() ?: return "Something went wrong. Please try again."
    return when {
        "invalid login credentials" in raw || "invalid_credentials" in raw -> "Incorrect email or password."
        "user already registered" in raw || "already been registered" in raw ->
            "An account with this email already exists."
        "email address is invalid" in raw -> "Please enter a valid email address."
        "password should be at least" in raw || "weak_password" in raw -> "Password must be at least 6 characters."
        "redirect" in raw && "not allowed" in raw -> "Something went wrong. Please try again."
        "rate limit" in raw || "too many requests" in raw -> "Too many attempts. Please wait a moment and try again."
        "network" in raw || "unable to resolve" in raw || "connect" in raw ->
            "Network error. Please check your connection."
        else -> if (isLogin) "Sign in failed. Please try again." else "Registration failed. Please try again."
    }
}
