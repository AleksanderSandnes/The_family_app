package com.sandnes.familyapp.ui.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.ProfileUpdate
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** ProfileViewModel against a fake Supabase backend (Auth session + Storage + Postgrest). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var app: Application
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val ada = UserModel(id = "u1", name = "Ada", email = "ada@example.com", birthday = "", mobile = "", avatarUrl = "https://old")

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        backend.signIn("auth-1").install()
        backend.onJson(HttpMethod.Post, "/storage/v1/object/avatars", """{"Key":"avatars/auth-1/avatar.jpg","Id":"1"}""")
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        coEvery { repo.getUser("u1") } returns ada
    }

    @After
    fun tearDown() = backend.uninstall()

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): ProfileViewModel {
        val vm = ProfileViewModel(app, repo)
        userId.value = "u1"
        settle { vm.user.value != null }
        return vm
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun `loads the signed-in user and clears it on sign-out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals("Ada", vm.user.value?.name)
            userId.value = null
            settle { vm.user.value == null }
        }

    @Test
    fun `refresh reloads the user`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.getUser("u1") } returns ada.copy(name = "Ada L")
            vm.refresh()
            settle { vm.user.value?.name == "Ada L" }
        }

    @Test
    fun `google sign-ups with a blank phone or birthday are asked to complete their profile`() =
        runTest(dispatcherRule.dispatcher) {
            backend.signIn("auth-1", provider = "google")
            val vm = loaded()
            val states = mutableListOf<Boolean>()
            val collector = launchCollect(vm, states)
            settle { states.lastOrNull() == true }
            vm.dismissProfileCompletion()
            settle { states.lastOrNull() == false }
            collector.cancel()
        }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(
        vm: ProfileViewModel,
        into: MutableList<Boolean>,
    ): Job =
        launch {
            vm.needsProfileCompletion.collect { into += it }
        }

    @Test
    fun `email sign-ups are never prompted to complete their profile`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val states = mutableListOf<Boolean>()
            val collector = launchCollect(vm, states)
            settle { states.isNotEmpty() }
            assertFalse(states.last())
            collector.cancel()
        }

    @Test
    fun `completeGoogleProfile merges trimmed values and keeps existing ones for blanks`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.completeGoogleProfile(" 555 ", "  "))
            coVerify { repo.updateProfile("u1", ProfileUpdate("Ada", "ada@example.com", "", "555", "https://old")) }
            assertEquals("555", vm.user.value?.mobile)
        }

    @Test
    fun `completeGoogleProfile and save do nothing while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = ProfileViewModel(app, repo)
            finish(vm.completeGoogleProfile("1", "2"))
            finish(vm.save("a", "b", "c", "d"))
            finish(vm.removeAvatar())
            coVerify(exactly = 0) { repo.updateProfile(any(), any()) }
        }

    @Test
    fun `save trims every field and preserves the avatar`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.save(" Grace ", " g@x.y ", " 1990-01-01 ", " 123 "))
            coVerify { repo.updateProfile("u1", ProfileUpdate("Grace", "g@x.y", "1990-01-01", "123", "https://old")) }
            assertEquals("Grace", vm.user.value?.name)
        }

    @Test
    fun `signOut signs out and then calls back`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            var done = false
            finish(vm.signOut { done = true })
            assertTrue(done)
            coVerify { repo.signOut() }
        }

    @Test
    fun `picking a photo uploads it and stores the public url`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val uri = Uri.parse("content://media/photo/1")
            shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpeg()))
            finish(vm.saveAvatarFromUri(app, uri))
            settle {
                !vm.isUploading.value &&
                    vm.user.value
                        ?.avatarUrl
                        ?.contains("auth-1/avatar.jpg") == true
            }
            assertTrue(backend.requestsTo("/storage/v1/object/avatars/auth-1/avatar.jpg", HttpMethod.Post).isNotEmpty())
            assertNull(vm.error.value)
        }

    @Test
    fun `an unreadable photo reports an error that can be cleared`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.saveAvatarFromUri(app, Uri.parse("content://media/missing")))
            assertEquals(app.getString(R.string.could_not_read_the_selected_photo), vm.error.value)
            vm.clearError()
            assertNull(vm.error.value)
        }

    @Test
    fun `a failing upload reports an update error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/storage/v1/object/avatars", "{}", HttpStatusCode.InternalServerError)
            val uri = Uri.parse("content://media/photo/2")
            shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpeg()))
            finish(vm.saveAvatarFromUri(app, uri))
            settle { vm.error.value != null }
            assertEquals(app.getString(R.string.failed_to_update_photo_please_try_again), vm.error.value)
        }

    @Test
    fun `camera capture is uploaded after a successful shot`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            // FileProvider caches its roots per process, so the Uri may be null in later tests;
            // the pending file is recorded either way.
            vm.prepareCameraCapture(app as Context)
            val file = java.io.File(app.cacheDir, "camera_captures/avatar_pending.jpg")
            file.writeBytes(jpeg())
            finish(vm.onCameraResult(true))
            settle {
                vm.user.value
                    ?.avatarUrl
                    ?.contains("auth-1/avatar.jpg") == true
            }
            settle { !file.exists() }
        }

    @Test
    fun `a cancelled camera capture or a missing file is ignored`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.onCameraResult(false))
            finish(vm.onCameraResult(true))
            assertNull(vm.error.value)
        }

    @Test
    fun `an unreadable camera file reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            vm.prepareCameraCapture(app)
            java.io.File(app.cacheDir, "camera_captures/avatar_pending.jpg").delete()
            finish(vm.onCameraResult(true))
            assertEquals(app.getString(R.string.could_not_read_the_captured_photo), vm.error.value)
        }

    @Test
    fun `removeAvatar deletes the stored image and clears the url`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.removeAvatar())
            assertTrue(backend.requestsTo("/storage/v1/object/avatars", HttpMethod.Delete).isNotEmpty())
            coVerify { repo.updateProfile("u1", ProfileUpdate("Ada", "ada@example.com", "", "", null)) }
            assertNull(vm.user.value?.avatarUrl)
        }

    @Test
    fun `removeAvatar failures are swallowed and leave the avatar`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Delete, "/storage/v1/object/avatars", "{}", HttpStatusCode.InternalServerError)
            finish(vm.removeAvatar())
            assertEquals("https://old", vm.user.value?.avatarUrl)
        }
}
