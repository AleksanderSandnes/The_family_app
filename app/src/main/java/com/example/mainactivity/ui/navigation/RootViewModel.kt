package com.example.mainactivity.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AuthGate {
    data object Loading : AuthGate

    data object SignedOut : AuthGate

    data object SignedIn : AuthGate
}

class RootViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    val gate: StateFlow<AuthGate> =
        repo.currentUserId
            .map { if (it == null) AuthGate.SignedOut else AuthGate.SignedIn }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthGate.Loading)
}
