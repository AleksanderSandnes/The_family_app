package com.example.mainactivity.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SessionManager(private val context: Context) {

    private val userIdKey = longPreferencesKey("current_user_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val currentUserId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[userIdKey].takeIf { it != null && it > 0 }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun signIn(userId: Long) {
        context.dataStore.edit { it[userIdKey] = userId }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.remove(userIdKey) }
    }
}
