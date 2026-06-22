package com.example.mainactivity.ui.auth

import android.app.Application
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
    val success: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(error = null) }

    fun login(email: String, password: String) {
        if (!validate(email = email, password = password)) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = repo.login(email, password)
            _state.update {
                result.fold(
                    onSuccess = { AuthUiState(success = true) },
                    onFailure = { e -> AuthUiState(error = e.message ?: "Sign in failed") }
                )
            }
        }
    }

    fun register(name: String, email: String, password: String, confirm: String, birthday: String, mobile: String) {
        when {
            name.isBlank() -> return fail("Please enter your name.")
            !validate(email = email, password = password) -> return
            password != confirm -> return fail("Passwords do not match.")
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = repo.register(name, email, password, birthday, mobile)
            _state.update {
                result.fold(
                    onSuccess = { AuthUiState(success = true) },
                    onFailure = { e -> AuthUiState(error = e.message ?: "Registration failed") }
                )
            }
        }
    }

    private fun validate(email: String, password: String): Boolean {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            fail("Please enter a valid email address."); return false
        }
        if (password.length < 6) {
            fail("Password must be at least 6 characters."); return false
        }
        return true
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, error = message) }
}
