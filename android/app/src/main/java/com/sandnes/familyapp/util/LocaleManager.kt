package com.sandnes.familyapp.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sandnes.familyapp.data.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Applies the in-app language override (Settings → Language) via AndroidX per-app locales.
 *
 * This is independent of the device locale: the chosen BCP-47 tag ("en"/"nb") is pushed to
 * [AppCompatDelegate.setApplicationLocales], which — with an AppCompat-based activity — wraps
 * the base context and re-creates the activity so every `stringResource` re-localizes live.
 * "system" (or blank) resets to an empty locale list, so the app follows the device language.
 *
 * Persistence across process restarts is driven by [SessionManager] (the `app_language` pref):
 * [applyPersisted] re-applies the stored tag on app start from `MainApplication.onCreate`.
 */
object LocaleManager {
    /** Language tags understood by the picker. "system" means "follow the device locale". */
    const val SYSTEM = "system"

    /** Push [tag] to the AppCompat per-app locale list. Must be called on the main thread. */
    fun apply(tag: String) {
        val locales =
            if (tag.isBlank() || tag == SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Read the persisted language pref once and apply it. Call on the main thread at app start. */
    fun applyPersisted(context: Context) {
        val tag = runBlocking { SessionManager.get(context).appLanguage.first() }
        apply(tag)
    }
}
