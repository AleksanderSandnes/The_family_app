package com.example.mainactivity.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FamilyViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    private val _family = MutableStateFlow<FamilyModel?>(null)
    val family: StateFlow<FamilyModel?> = _family.asStateFlow()

    private val _members = MutableStateFlow<List<UserModel>>(emptyList())
    val members: StateFlow<List<UserModel>> = _members.asStateFlow()

    private val _currentUser = MutableStateFlow<UserModel?>(null)
    val currentUser: StateFlow<UserModel?> = _currentUser.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) {
                    load(userId)
                } else {
                    _family.value = null
                    _members.value = emptyList()
                    _currentUser.value = null
                }
            }
        }
    }

    fun refresh() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            load(userId)
        }

    private suspend fun load(userId: String) {
        runCatching {
            val user = repo.getUser(userId) ?: return
            _currentUser.value = user
            if (user.familyId != null) {
                _family.value = repo.getFamily(user.familyId)
                _members.value =
                    SupabaseManager.client.postgrest
                        .from("users")
                        .select { filter { eq("family_id", user.familyId) } }
                        .decodeList<UserModel>()
            } else {
                _family.value = null
                _members.value = emptyList()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun createFamily(
        name: String,
        code: String,
    ) =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            repo
                .createFamily(name, code, userId)
                .onSuccess { load(userId) }
                .onFailure { _error.value = it.message }
        }

    fun joinFamily(
        name: String,
        code: String,
    ) =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            repo
                .joinFamily(name, code, userId)
                .onSuccess { load(userId) }
                .onFailure { _error.value = it.message }
        }

    fun leaveFamily() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            repo.leaveFamily(userId)
            load(userId)
        }
}
