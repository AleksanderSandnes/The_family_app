package com.example.mainactivity.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthGate {
    data object Loading : AuthGate

    data object SignedOut : AuthGate

    data object NeedsPermissions : AuthGate

    data object SignedIn : AuthGate
}

class RootViewModel(
    app: Application,
    internal val repo: FamilyRepository = FamilyRepository.get(app),
) : AndroidViewModel(app) {
    val gate: StateFlow<AuthGate> =
        combine(
            repo.currentUserId,
            repo.permissionsRequested,
        ) { userId, permsDone ->
            when {
                userId == null -> AuthGate.SignedOut
                !permsDone -> AuthGate.NeedsPermissions
                else -> AuthGate.SignedIn
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthGate.Loading)

    fun completePermissionsOnboarding() =
        viewModelScope.launch {
            repo.setPermissionsRequested()
        }
}
