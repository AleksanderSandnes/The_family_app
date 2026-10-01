package com.sandnes.familyapp.testutil

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.DialogProperties
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base for Robolectric Compose screen tests: a tall window so sheets fit, plus a fake Supabase
 * backend installed for the real ViewModels the screens are driven by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-mdpi")
abstract class ComposeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    val backend = FakeSupabase()

    @Before
    fun installBackend() {
        backend.install()
        // Under Robolectric a platform-default-width dialog that hosts a full-width text field never
        // settles its window size (Compose never goes idle). Give dialogs a plain full-window layout.
        mockkConstructor(DialogProperties::class)
        every { anyConstructed<DialogProperties>().usePlatformDefaultWidth } returns false
    }

    @After
    fun uninstallBackend() {
        unmockkConstructor(DialogProperties::class)
        backend.uninstall()
    }

    /** A relaxed repository signed in as [userId] who belongs to [familyId]. */
    fun fakeRepo(
        userId: String = "u1",
        familyId: String? = "f1",
        admin: Boolean = true,
        configure: FamilyRepository.() -> Unit = {},
    ): FamilyRepository {
        val repo = mockk<FamilyRepository>(relaxed = true)
        every { repo.currentUserId } returns MutableStateFlow(userId)
        every { repo.familyChanged } returns MutableSharedFlow()
        coEvery { repo.getUser(any()) } returns UserModel(id = userId, name = "Ada", familyId = familyId)
        coEvery { repo.isFamilyAdmin(any()) } returns admin
        repo.configure()
        return repo
    }

    fun resetCache(vmClass: Class<*>) {
        val f = vmClass.getDeclaredField("cache")
        f.isAccessible = true
        f.set(null, if (f.type == Map::class.java) emptyMap<Any, Any>() else emptyList<Any?>())
    }
}
