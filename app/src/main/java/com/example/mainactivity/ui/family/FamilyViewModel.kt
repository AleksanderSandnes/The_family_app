package com.example.mainactivity.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyEntity
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    val family: StateFlow<FamilyEntity?> =
        repo.currentFamily.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<UserEntity>> = repo.currentUser.flatMapLatest { user ->
        val fid = user?.familyId
        if (fid == null) flowOf(emptyList()) else repo.userDao.membersOfFamily(fid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser: StateFlow<UserEntity?> =
        repo.currentUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    fun createFamily(name: String, code: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        repo.createFamily(name, code, userId)
    }

    fun joinFamily(name: String, code: String) = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        repo.joinFamily(name, code, userId).onFailure { _error.value = it.message }
    }

    fun leaveFamily() = viewModelScope.launch {
        val userId = repo.currentUserId.first() ?: return@launch
        repo.leaveFamily(userId)
    }
}
