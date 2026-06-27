package com.example.mainactivity.ui.settings

import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ThemeMode
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [SettingsViewModel].
 *
 * The VM exposes four [StateFlow]s backed by [FamilyRepository] flows, and four mutating
 * functions that delegate to the repository (plus a WorkManager side-effect in
 * [SettingsViewModel.setNotificationsEnabled]).
 *
 * [FamilyRepository] is mocked with mockk. WorkManager statics are mocked in tests
 * that exercise [SettingsViewModel.setNotificationsEnabled].
 */
@RunWith(JUnit4::class)
class SettingsViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var themeMode: MutableStateFlow<ThemeMode>
    private lateinit var notificationsEnabled: MutableStateFlow<Boolean>
    private lateinit var notifyDaysBefore: MutableStateFlow<Int>
    private lateinit var locationVisible: MutableStateFlow<Boolean>
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        notificationsEnabled = MutableStateFlow(true)
        notifyDaysBefore = MutableStateFlow(1)
        locationVisible = MutableStateFlow(false)

        every { repo.themeMode } returns themeMode
        every { repo.notificationsEnabled } returns notificationsEnabled
        every { repo.notifyDaysBefore } returns notifyDaysBefore
        every { repo.locationVisible } returns locationVisible

        vm = SettingsViewModel(repo)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial StateFlow values — read before any mutation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial themeMode is SYSTEM`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)
        }

    @Test
    fun `initial notificationsEnabled is true`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertTrue(vm.notificationsEnabled.value)
        }

    @Test
    fun `initial notifyDaysBefore is 1`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertEquals(1, vm.notifyDaysBefore.value)
        }

    @Test
    fun `initial locationVisible is false`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()
            assertFalse(vm.locationVisible.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. StateFlow reflects upstream flow changes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `themeMode StateFlow reflects repo flow change to DARK`() =
        runTest(dispatcherRule.dispatcher) {
            backgroundScope.launch { vm.themeMode.collect {} } // activate WhileSubscribed
            themeMode.value = ThemeMode.DARK
            advanceUntilIdle()
            assertEquals(ThemeMode.DARK, vm.themeMode.value)
        }

    @Test
    fun `notificationsEnabled StateFlow reflects repo flow change to false`() =
        runTest(dispatcherRule.dispatcher) {
            backgroundScope.launch { vm.notificationsEnabled.collect {} }
            notificationsEnabled.value = false
            advanceUntilIdle()
            assertFalse(vm.notificationsEnabled.value)
        }

    @Test
    fun `notifyDaysBefore StateFlow reflects repo flow change to 7`() =
        runTest(dispatcherRule.dispatcher) {
            backgroundScope.launch { vm.notifyDaysBefore.collect {} }
            notifyDaysBefore.value = 7
            advanceUntilIdle()
            assertEquals(7, vm.notifyDaysBefore.value)
        }

    @Test
    fun `locationVisible StateFlow reflects repo flow change to true`() =
        runTest(dispatcherRule.dispatcher) {
            backgroundScope.launch { vm.locationVisible.collect {} }
            locationVisible.value = true
            advanceUntilIdle()
            assertTrue(vm.locationVisible.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. setThemeMode — delegates to repo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setThemeMode DARK delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setThemeMode(ThemeMode.DARK)
            advanceUntilIdle()
            coVerify { repo.setThemeMode(ThemeMode.DARK) }
        }

    @Test
    fun `setThemeMode LIGHT delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setThemeMode(ThemeMode.LIGHT)
            advanceUntilIdle()
            coVerify { repo.setThemeMode(ThemeMode.LIGHT) }
        }

    @Test
    fun `setThemeMode SYSTEM delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setThemeMode(ThemeMode.SYSTEM)
            advanceUntilIdle()
            coVerify { repo.setThemeMode(ThemeMode.SYSTEM) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. setNotifyDaysBefore — delegates to repo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setNotifyDaysBefore 3 delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setNotifyDaysBefore(3)
            advanceUntilIdle()
            coVerify { repo.setNotifyDaysBefore(3) }
        }

    @Test
    fun `setNotifyDaysBefore 0 delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setNotifyDaysBefore(0)
            advanceUntilIdle()
            coVerify { repo.setNotifyDaysBefore(0) }
        }

    @Test
    fun `setNotifyDaysBefore 7 delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setNotifyDaysBefore(7)
            advanceUntilIdle()
            coVerify { repo.setNotifyDaysBefore(7) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. setLocationVisible — delegates to repo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setLocationVisible true delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setLocationVisible(true)
            advanceUntilIdle()
            coVerify { repo.setLocationVisible(true) }
        }

    @Test
    fun `setLocationVisible false delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setLocationVisible(false)
            advanceUntilIdle()
            coVerify { repo.setLocationVisible(false) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. setNotificationsEnabled — delegates to repo (server push handles delivery)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `setNotificationsEnabled true delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setNotificationsEnabled(true)
            advanceUntilIdle()
            coVerify { repo.setNotificationsEnabled(true) }
        }

    @Test
    fun `setNotificationsEnabled false delegates to repo`() =
        runTest(dispatcherRule.dispatcher) {
            vm.setNotificationsEnabled(false)
            advanceUntilIdle()
            coVerify { repo.setNotificationsEnabled(false) }
        }
}
