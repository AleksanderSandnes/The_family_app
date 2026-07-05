package com.example.mainactivity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionManagerTest {
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessionManager = SessionManager(context)
        // The Preferences DataStore is a process singleton keyed by file name, so writes
        // leak across tests in the suite. Reset to defaults before each test.
        runBlocking {
            sessionManager.signOut()
            sessionManager.setThemeMode(ThemeMode.SYSTEM)
            sessionManager.setNotificationsEnabled(true)
            sessionManager.setNotifyDaysBefore(1)
            sessionManager.setLocationVisible(false)
        }
    }

    // --- Default values ---

    @Test
    fun `currentUserId defaults to null`() =
        runTest {
            assertNull(sessionManager.currentUserId.first())
        }

    @Test
    fun `notificationsEnabled defaults to true`() =
        runTest {
            assertTrue(sessionManager.notificationsEnabled.first())
        }

    @Test
    fun `notifyDaysBefore defaults to 1`() =
        runTest {
            assertEquals(1, sessionManager.notifyDaysBefore.first())
        }

    @Test
    fun `locationVisible defaults to false`() =
        runTest {
            assertFalse(sessionManager.locationVisible.first())
        }

    @Test
    fun `permissionsRequested defaults to false`() =
        runTest {
            assertFalse(sessionManager.permissionsRequested.first())
        }

    @Test
    fun `themeMode defaults to SYSTEM`() =
        runTest {
            assertEquals(ThemeMode.SYSTEM, sessionManager.themeMode.first())
        }

    // --- Sign in / Sign out lifecycle ---

    @Test
    fun `signIn persists userId and signOut clears it`() =
        runTest {
            sessionManager.signIn("user-123")
            assertEquals("user-123", sessionManager.currentUserId.first())
            sessionManager.signOut()
            assertNull(sessionManager.currentUserId.first())
        }

    @Test
    fun `signIn twice overwrites userId`() =
        runTest {
            sessionManager.signIn("user-1")
            sessionManager.signIn("user-2")
            assertEquals("user-2", sessionManager.currentUserId.first())
        }

    // --- Settings setters ---

    @Test
    fun `setNotificationsEnabled updates flow`() =
        runTest {
            sessionManager.setNotificationsEnabled(false)
            assertFalse(sessionManager.notificationsEnabled.first())
            sessionManager.setNotificationsEnabled(true)
            assertTrue(sessionManager.notificationsEnabled.first())
        }

    @Test
    fun `setThemeMode to LIGHT updates flow`() =
        runTest {
            sessionManager.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, sessionManager.themeMode.first())
        }

    @Test
    fun `setThemeMode to DARK updates flow`() =
        runTest {
            sessionManager.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, sessionManager.themeMode.first())
        }

    @Test
    fun `setThemeMode to SYSTEM updates flow`() =
        runTest {
            sessionManager.setThemeMode(ThemeMode.DARK)
            sessionManager.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, sessionManager.themeMode.first())
        }

    @Test
    fun `setNotifyDaysBefore updates flow`() =
        runTest {
            sessionManager.setNotifyDaysBefore(7)
            assertEquals(7, sessionManager.notifyDaysBefore.first())
        }

    @Test
    fun `setLocationVisible to false updates flow`() =
        runTest {
            sessionManager.setLocationVisible(false)
            assertFalse(sessionManager.locationVisible.first())
        }

    @Test
    fun `setLocationVisible to true updates flow`() =
        runTest {
            sessionManager.setLocationVisible(true)
            assertTrue(sessionManager.locationVisible.first())
        }

    @Test
    fun `setPermissionsRequested sets flag to true`() =
        runTest {
            sessionManager.setPermissionsRequested()
            assertTrue(sessionManager.permissionsRequested.first())
        }
}
