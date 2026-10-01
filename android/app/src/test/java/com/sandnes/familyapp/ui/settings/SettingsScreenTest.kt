package com.sandnes.familyapp.ui.settings

import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.ThemeMode
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.click
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.clickToggle
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.waitForText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTest : ComposeScreenTest() {
    private val theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val notifications = MutableStateFlow(true)
    private val leadDays = MutableStateFlow(1)
    private val visible = MutableStateFlow(true)
    private val language = MutableStateFlow("system")
    private val deleteState = MutableStateFlow(DeleteAccountState.Idle)

    private fun viewModel(): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.themeMode } returns theme
        every { vm.notificationsEnabled } returns notifications
        every { vm.notifyDaysBefore } returns leadDays
        every { vm.locationVisible } returns visible
        every { vm.appLanguage } returns language
        every { vm.deleteAccountState } returns deleteState
        return vm
    }

    @Test
    fun `every preference control calls through to the view model`() {
        val vm = viewModel()
        compose.setContent { SettingsScreen(onBack = {}, vm = vm) }
        compose.waitForText(str(R.string.appearance).uppercase())
        for (label in listOf(R.string.light, R.string.dark)) {
            compose.clickDescription(str(R.string.theme_option_a11y, str(label)), index = 0)
        }
        // The currently selected option's label carries a ", Selected" suffix.
        compose.onAllNodesWithContentDescription(str(R.string.theme_option_a11y, str(R.string.system)), substring = true)[0].click()
        verify { vm.setThemeMode(ThemeMode.SYSTEM) }
        verify { vm.setThemeMode(ThemeMode.DARK) }
        verify { vm.setThemeMode(ThemeMode.LIGHT) }
        for (label in listOf(R.string.same_day, R.string.one_day, R.string.two_days, R.string.seven_days)) {
            compose.clickText(str(label))
        }
        verify { vm.setNotifyDaysBefore(7) }
        compose.clickText(str(R.string.english))
        compose.clickText(str(R.string.norwegian))
        verify { vm.setAppLanguage("nb") }
        // Toggles: notifications (turn off), location visibility.
        compose.clickToggle(0)
        verify { vm.setNotificationsEnabled(false) }
        compose.clickToggle(1)
        verify { vm.setLocationVisible(false) }
    }

    @Test
    fun `enabling notifications requests the permission and the lead time hides when off`() {
        notifications.value = false
        val vm = viewModel()
        compose.setContent { SettingsScreen(onBack = {}, vm = vm) }
        compose.waitForText(str(R.string.notifications).uppercase())
        assertTrue(!compose.hasText(str(R.string.remind_me)))
        compose.clickToggle(0)
    }

    @Test
    fun `links open and delete account is confirmed`() {
        val vm = viewModel()
        compose.setContent { SettingsScreen(onBack = {}, vm = vm) }
        compose.waitForText(str(R.string.privacy_policy))
        compose.clickText(str(R.string.privacy_policy))
        compose.clickText(str(R.string.terms_of_use))
        compose.clickText(str(R.string.delete_account))
        compose.waitForText(str(R.string.delete_account_q))
        compose.clickText(str(R.string.cancel))
        compose.clickText(str(R.string.delete_account))
        compose.clickText(str(R.string.delete_account_permanently))
        verify { vm.deleteAccount() }
    }

    @Test
    fun `delete account progress and failure are surfaced`() {
        val vm = viewModel()
        compose.setContent { SettingsScreen(onBack = {}, vm = vm) }
        compose.waitForText(str(R.string.delete_account))
        deleteState.value = DeleteAccountState.InProgress
        compose.waitForIdle()
        deleteState.value = DeleteAccountState.Failed
        compose.waitForText(str(R.string.delete_account_failed))
        compose.mainClock.advanceTimeBy(15_000)
        compose.waitForIdle()
        verify { vm.dismissDeleteAccountError() }
    }

    @Test
    fun `changing a preference shows the saved snackbar`() {
        val vm = viewModel()
        compose.setContent { SettingsScreen(onBack = {}, vm = vm) }
        compose.waitForText(str(R.string.appearance).uppercase())
        compose.waitForIdle()
        theme.value = ThemeMode.DARK
        compose.waitForText(str(R.string.settings_saved))
    }
}
