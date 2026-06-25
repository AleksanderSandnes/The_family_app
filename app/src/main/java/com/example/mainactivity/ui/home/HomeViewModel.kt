package com.example.mainactivity.ui.home

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

data class HomeUiState(
    val user: UserModel? = null,
    val family: FamilyModel? = null,
    val memberCount: Int = 0,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
)

class HomeViewModel(
    app: Application,
    internal val repo: FamilyRepository = FamilyRepository.get(app),
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) load(userId) else _state.value = HomeUiState(isLoading = false)
            }
        }
    }

    fun refresh() =
        viewModelScope.launch {
            val userId = repo.currentUserId.first() ?: return@launch
            load(userId)
        }

    private suspend fun load(userId: String) {
        _state.value = _state.value.copy(isLoading = true, loadError = false)
        runCatching {
            val user = repo.getUser(userId)
            if (user == null) {
                _state.value = HomeUiState(isLoading = false, loadError = true)
                return
            }
            val family = user.familyId?.let { repo.getFamily(it) }
            val memberCount =
                if (user.familyId != null) {
                    SupabaseManager.client.postgrest
                        .from("users")
                        .select { filter { eq("family_id", user.familyId) } }
                        .decodeList<UserModel>()
                        .size
                } else {
                    0
                }
            _state.value = HomeUiState(user, family, memberCount, isLoading = false)
        }.onFailure {
            _state.value = _state.value.copy(isLoading = false, loadError = true)
        }
    }
}
