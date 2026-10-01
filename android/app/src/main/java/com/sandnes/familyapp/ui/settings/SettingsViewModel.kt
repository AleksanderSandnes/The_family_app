package com.sandnes.familyapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.SessionManager
import com.sandnes.familyapp.data.ThemeMode
import com.sandnes.familyapp.util.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeleteAccountState { Idle, InProgress, Failed }

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        internal val repo: FamilyRepository,
        private val session: SessionManager,
    ) : ViewModel() {
        val appLanguage: StateFlow<String> =
            session.appLanguage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocaleManager.SYSTEM)
        val themeMode: StateFlow<ThemeMode> =
            repo.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

        val notificationsEnabled: StateFlow<Boolean> =
            repo.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val notifyDaysBefore: StateFlow<Int> =
            repo.notifyDaysBefore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

        val locationVisible: StateFlow<Boolean> =
            repo.locationVisible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        private val _deleteAccountState = MutableStateFlow(DeleteAccountState.Idle)
        val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

        // On success the local session is cleared and RootViewModel switches to the signed-out
        // flow, so there is no success state to render here.
        fun deleteAccount() {
            if (_deleteAccountState.value == DeleteAccountState.InProgress) return
            _deleteAccountState.value = DeleteAccountState.InProgress
            viewModelScope.launch {
                _deleteAccountState.value =
                    if (repo.deleteAccount().isSuccess) DeleteAccountState.Idle else DeleteAccountState.Failed
            }
        }

        fun dismissDeleteAccountError() {
            _deleteAccountState.value = DeleteAccountState.Idle
        }

        fun setThemeMode(mode: ThemeMode) =
            viewModelScope.launch {
                repo.setThemeMode(mode)
            }

        // Birthday/event reminders are delivered by the server (daily-reminders Edge
        // Function) which reads the mirrored `notifications_enabled` flag, so the client
        // only needs to persist the preference — no local WorkManager to (de)schedule.
        fun setNotificationsEnabled(enabled: Boolean) =
            viewModelScope.launch {
                repo.setNotificationsEnabled(enabled)
            }

        fun setNotifyDaysBefore(days: Int) =
            viewModelScope.launch {
                repo.setNotifyDaysBefore(days)
            }

        fun setLocationVisible(enabled: Boolean) =
            viewModelScope.launch {
                repo.setLocationVisible(enabled)
            }

        // Persist the in-app language and apply it immediately so the UI re-localizes live.
        // viewModelScope runs on Dispatchers.Main.immediate, so the AppCompat call is on-thread.
        fun setAppLanguage(tag: String) =
            viewModelScope.launch {
                session.setAppLanguage(tag)
                LocaleManager.apply(tag)
            }
    }
