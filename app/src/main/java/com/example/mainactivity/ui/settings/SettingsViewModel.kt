package com.example.mainactivity.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ThemeMode
import com.example.mainactivity.workers.NotificationWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = FamilyRepository.get(app)

    val themeMode: StateFlow<ThemeMode> =
        repo.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val notificationsEnabled: StateFlow<Boolean> =
        repo.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifyDaysBefore: StateFlow<Int> =
        repo.notifyDaysBefore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val locationVisible: StateFlow<Boolean> =
        repo.locationVisible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) =
        viewModelScope.launch {
            repo.setThemeMode(mode)
        }

    fun setNotificationsEnabled(
        enabled: Boolean,
        context: Context,
    ) =
        viewModelScope.launch {
            repo.setNotificationsEnabled(enabled)
            if (enabled) NotificationWorker.schedule(context) else NotificationWorker.cancel(context)
        }

    fun setNotifyDaysBefore(days: Int) =
        viewModelScope.launch {
            repo.setNotifyDaysBefore(days)
        }

    fun setLocationVisible(enabled: Boolean) =
        viewModelScope.launch {
            repo.setLocationVisible(enabled)
        }
}
