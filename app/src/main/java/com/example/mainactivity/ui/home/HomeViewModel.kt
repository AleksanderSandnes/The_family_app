package com.example.mainactivity.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyEntity
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val user: UserEntity? = null,
    val family: FamilyEntity? = null,
    val memberCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    private val members: Flow<Int> = repo.currentUser.flatMapLatest { user ->
        val fid = user?.familyId
        if (fid == null) flowOf(0) else repo.userDao.membersOfFamily(fid).map { it.size }
    }

    val state = kotlinx.coroutines.flow.combine(repo.currentUser, repo.currentFamily, members) { user, family, count ->
        HomeUiState(user, family, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
