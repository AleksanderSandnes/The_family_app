package com.example.mainactivity.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthGate {
    data object Loading : AuthGate

    data object SignedOut : AuthGate

    data object NeedsPermissions : AuthGate

    data object SignedIn : AuthGate
}

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
    ) : ViewModel() {
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

        init {
            // Once fully signed in, register this device's FCM token and mirror the local
            // notification settings to the server so push delivery works and respects them.
            viewModelScope.launch {
                gate.collect { state ->
                    if (state is AuthGate.SignedIn) {
                        repo.syncPushToken()
                        repo.syncNotificationPrefsToServer()
                    }
                }
            }
        }

        fun completePermissionsOnboarding() =
            viewModelScope.launch {
                repo.setPermissionsRequested()
            }
    }
