package com.example.mainactivity.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "session")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val userIdKey = stringPreferencesKey("current_user_id_v2")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val notifyDaysBeforeKey = intPreferencesKey("notify_days_before")
    private val locationVisibleKey = booleanPreferencesKey("location_visible")
    private val permissionsRequestedKey = booleanPreferencesKey("permissions_requested")

    val currentUserId: Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[userIdKey]?.takeIf { it.isNotEmpty() }
        }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            when (prefs[themeModeKey]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[notificationsEnabledKey] ?: true
        }

    val notifyDaysBefore: Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[notifyDaysBeforeKey] ?: 1
        }

    val locationVisible: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[locationVisibleKey] ?: false
        }

    val permissionsRequested: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[permissionsRequestedKey] ?: false
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun setNotifyDaysBefore(days: Int) {
        context.dataStore.edit { it[notifyDaysBeforeKey] = days }
    }

    suspend fun setLocationVisible(enabled: Boolean) {
        context.dataStore.edit { it[locationVisibleKey] = enabled }
    }

    suspend fun setPermissionsRequested() {
        context.dataStore.edit { it[permissionsRequestedKey] = true }
    }

    suspend fun signIn(userId: String) {
        context.dataStore.edit { it[userIdKey] = userId }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.remove(userIdKey) }
    }

    companion object {
        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface SessionManagerEntryPoint {
            fun sessionManager(): SessionManager
        }

        fun get(context: Context): SessionManager =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SessionManagerEntryPoint::class.java,
            ).sessionManager()
    }
}
