package com.sandnes.familyapp.ui.profile

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.click
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.replaceText
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileScreensTest : ComposeScreenTest() {
    private val user = MutableStateFlow<UserModel?>(UserModel(id = "u1", name = "Ada Lovelace", email = "ada@example.com", mobile = "12345678", birthday = "1990-05-17"))
    private val error = MutableStateFlow<String?>(null)
    private val uploading = MutableStateFlow(false)
    private val needsCompletion = MutableStateFlow(false)

    private fun viewModel(): ProfileViewModel {
        val vm = mockk<ProfileViewModel>(relaxed = true)
        every { vm.user } returns user
        every { vm.error } returns error
        every { vm.isUploading } returns uploading
        every { vm.needsProfileCompletion } returns needsCompletion
        return vm
    }

    @Test
    fun `profile shows the user's details and navigates`() {
        var edit = 0
        var settings = 0
        val vm = viewModel()
        compose.setContent { ProfileScreen(onEdit = { edit++ }, onSettings = { settings++ }, onSignedOut = {}, viewModel = vm) }
        compose.waitForText("Ada Lovelace")
        assertTrue(compose.hasText("May 17, 1990"))
        compose.clickText(str(R.string.edit_profile))
        compose.clickText(str(R.string.settings))
        assertEquals(1, edit)
        assertEquals(1, settings)
    }

    @Test
    fun `missing details show dashes and a bad birthday is shown as typed`() {
        user.value = UserModel(id = "u1", name = "", email = "", mobile = "", birthday = "")
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText("—", substring = true)
        user.value = UserModel(id = "u1", name = "Bo", birthday = "someday")
        compose.waitForText("someday")
    }

    private fun openAvatarPicker() {
        // The avatar circle is the first clickable thing in the profile card.
        val count = compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size
        for (i in 0 until count) {
            compose.onAllNodes(androidx.compose.ui.test.hasClickAction())[i].click()
            compose.waitForIdle()
            if (compose.hasText(str(R.string.profile_photo))) return
        }
        error("avatar picker never opened")
    }

    @Test
    fun `avatar picker offers camera gallery and removal`() {
        user.value = UserModel(id = "u1", name = "Ada", avatarUrl = "https://img.example/a.png")
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText("Ada")
        openAvatarPicker()
        compose.clickText(str(R.string.take_photo))
        openAvatarPicker()
        compose.clickText(str(R.string.choose_from_gallery))
        openAvatarPicker()
        compose.clickText(str(R.string.remove_photo))
        verify { vm.removeAvatar() }
        openAvatarPicker()
        compose.clickText(str(R.string.cancel))
    }

    @Test
    fun `avatar picker hides removal when there is no photo`() {
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText("Ada Lovelace")
        openAvatarPicker()
        assertTrue(!compose.hasText(str(R.string.remove_photo)))
    }

    @Test
    fun `signing out is confirmed`() {
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText("Ada Lovelace")
        compose.clickText(str(R.string.sign_out))
        compose.waitForText(str(R.string.sign_out_q))
        compose.clickText(str(R.string.cancel))
        compose.clickText(str(R.string.sign_out))
        compose.clickText(str(R.string.sign_out), index = 1)
        verify { vm.signOut(any()) }
    }

    @Test
    fun `error banner and upload state render`() {
        error.value = "Could not save"
        uploading.value = true
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText("Could not save")
    }

    @Test
    fun `google sign ups are asked to complete their profile`() {
        needsCompletion.value = true
        val vm = viewModel()
        compose.setContent { ProfileScreen({}, {}, {}, vm) }
        compose.waitForText(str(R.string.complete_your_profile))
        compose.typeInto("99887766")
        compose.clickText(str(R.string.save))
        verify { vm.completeGoogleProfile("99887766", "") }
        compose.clickText(str(R.string.skip_for_now))
        verify { vm.dismissProfileCompletion() }
    }

    @Test
    fun `edit screen saves the changed fields and goes back`() {
        var back = 0
        val vm = viewModel()
        compose.setContent { ProfileEditScreen(onBack = { back++ }, viewModel = vm) }
        compose.waitForText(str(R.string.personal_information))
        compose.replaceText("Ada Lovelace Jr", 0)
        compose.clickText(str(R.string.save_changes))
        verify { vm.save("Ada Lovelace Jr", "ada@example.com", "1990-05-17", "12345678") }
        assertEquals(1, back)
    }
}
