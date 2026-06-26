package com.example.mainactivity.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.data.remote.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val user: UserModel? = null,
    val family: FamilyModel? = null,
    val memberCount: Int = 0,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    internal val repo: FamilyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.currentUserId.collect { userId ->
                if (userId != null) load(userId) else _state.value = HomeUiState(isLoading = false)
            }
        }
        viewModelScope.launch {
            repo.familyChanged.collect {
                val userId = repo.currentUserId.first() ?: return@collect
                load(userId)
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
