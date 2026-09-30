package com.sandnes.familyapp.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Use only dedicated fixture preferences and Keystore aliases, never a real session. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AndroidSecretStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store() = AndroidSecretStore(context, "family_auth_instrumented", "auth_instrumented")

    @Test
    fun ciphertextSurvivesRecreationAndDeleteClearsIt() =
        runBlocking {
            val preferences = context.getSharedPreferences("auth_instrumented", Context.MODE_PRIVATE)
            val storage = store()
            val secret = "fictional-session-😀"
            storage.write("round_trip", secret)
            val ciphertext = preferences.getString("round_trip", null)
            assertTrue(!ciphertext.isNullOrEmpty())
            assertFalse(ciphertext!!.contains(secret))
            assertEquals(secret, store().read("round_trip"))
            storage.remove("round_trip")
            assertNull(store().read("round_trip"))
        }

    // Run separately with adb instrumentation, restarting the app process between them.
    @Test
    fun aSeedRestartFixture() =
        runBlocking {
            store().write("restart_fixture", "fictional-persisted-session")
        }

    @Test
    fun bRestoreRestartFixture() =
        runBlocking {
            assertEquals("fictional-persisted-session", store().read("restart_fixture"))
            store().remove("restart_fixture")
        }

    @Test
    fun migrationRemovesLegacyPreferencesAfterKeystorePersistence() =
        runBlocking {
            val preferences = context.getSharedPreferences("legacy_auth_instrumented", Context.MODE_PRIVATE)
            val legacy = SettingsSessionManager(SharedPreferencesSettings(preferences))
            val session = UserSession("fictional-access", "fictional-refresh", expiresIn = 3600, tokenType = "bearer")
            legacy.saveSession(session)
            val migrated = EncryptedAuthStorage(store(), legacy, MemoryCodeVerifierCache())
            try {
                assertEquals(session, migrated.loadSession())
                assertFalse(preferences.contains("session"))
                assertEquals(session, migrated.loadSession())
            } finally {
                migrated.deleteSession()
            }
        }
}
