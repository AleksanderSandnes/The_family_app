package com.sandnes.familyapp.ui.family

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.FamilyModel
import com.sandnes.familyapp.data.FamilyRepository
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

/** FamilyViewModel against a fake Supabase backend (REST, storage and relations). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FamilyViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var app: Application
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val ada = UserModel(id = "u1", name = "Ada", familyId = "f1")

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        backend.install()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.pendingJoinCode } returns MutableStateFlow("CODE")
        coEvery { repo.getUser("u1") } returns ada
        coEvery { repo.getFamily("f1") } returns FamilyModel(id = "f1", name = "Fam")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/users",
            """[{"id":"u1","name":"Ada","family_id":"f1"},{"id":"u2","name":"Bob","family_id":"f1"}]""",
        )
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/family_relations",
            """[{"id":"r1","family_id":"f1","from_user_id":"u1","to_user_id":"u2","relation":"Brother"},
                {"id":"r2","family_id":"f1","from_user_id":"u2","to_user_id":"u1","relation":"Sister"}]""",
        )
    }

    @After
    fun tearDown() = backend.uninstall()

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): FamilyViewModel {
        val vm = FamilyViewModel(app, repo)
        userId.value = "u1"
        settle { vm.members.value.isNotEmpty() && vm.relations.value.isNotEmpty() }
        return vm
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun `loads the family members and only my relations`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals("Fam", vm.family.value?.name)
            assertEquals(listOf("Ada", "Bob"), vm.members.value.map { it.name })
            assertEquals(mapOf("u2" to "Brother"), vm.relations.value)
            assertEquals("Ada", vm.currentUser.value?.name)
            assertEquals("CODE", vm.pendingJoinCode.value)
        }

    @Test
    fun `a user without a family has no family members or relations`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            val vm = FamilyViewModel(app, repo)
            userId.value = "u1"
            settle { vm.currentUser.value != null }
            assertNull(vm.family.value)
            assertTrue(vm.members.value.isEmpty() && vm.relations.value.isEmpty())
        }

    @Test
    fun `sign out clears everything and a missing profile keeps the old state`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            userId.value = null
            settle { vm.currentUser.value == null && vm.family.value == null }
            coEvery { repo.getUser("u1") } returns null
            userId.value = "u1"
            finish(vm.refresh())
            assertNull(vm.currentUser.value)
        }

    @Test
    fun `refresh reloads and is ignored while signed out`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("rest/v1/users", HttpMethod.Get).size
            finish(vm.refresh())
            assertTrue(backend.requestsTo("rest/v1/users", HttpMethod.Get).size > before)
            userId.value = null
            finish(vm.refresh())
        }

    @Test
    fun `setting a relation updates locally and persists, blank clears it`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.setRelation("u3", "Cousin"))
            assertEquals("Cousin", vm.relations.value["u3"])
            assertTrue(
                backend
                    .requestsTo("family_relations", HttpMethod.Post)
                    .single()
                    .body
                    .contains("Cousin"),
            )
            finish(vm.setRelation("u3", " "))
            assertNull(vm.relations.value["u3"])
            assertEquals(1, backend.requestsTo("family_relations", HttpMethod.Delete).size)
            userId.value = null
            finish(vm.setRelation("u3", "x"))
        }

    @Test
    fun `relations need a loaded family`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = null)
            val vm = FamilyViewModel(app, repo)
            userId.value = "u1"
            settle { vm.currentUser.value != null }
            finish(vm.setRelation("u2", "x"))
            assertTrue(backend.requestsTo("family_relations", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `creating a family reloads on success and surfaces failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.createFamily("New", "CODE", "u1") } returns Result.success("f9")
            finish(vm.createFamily("New", "CODE"))
            assertNull(vm.error.value)
            coEvery { repo.createFamily("Bad", "CODE", "u1") } returns Result.failure(IllegalStateException("taken"))
            finish(vm.createFamily("Bad", "CODE"))
            assertEquals("taken", vm.error.value)
            vm.clearError()
            assertNull(vm.error.value)
            userId.value = null
            finish(vm.createFamily("X", "Y"))
        }

    @Test
    fun `joining a family with existing members prompts relation setup`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.joinFamily("ABC", "u1") } returns Result.success("f1")
            finish(vm.joinFamily("ABC"))
            settle { vm.promptRelationSetup.value }
            vm.dismissRelationSetup()
            assertFalse(vm.promptRelationSetup.value)
        }

    @Test
    fun `joining fails or is alone without prompting`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.joinFamily("BAD", "u1") } returns Result.failure(IllegalStateException("Join code is incorrect."))
            finish(vm.joinFamily("BAD"))
            assertEquals("Join code is incorrect.", vm.error.value)
            backend.onJson(HttpMethod.Get, "/rest/v1/users", """[{"id":"u1","name":"Ada","family_id":"f1"}]""")
            coEvery { repo.joinFamily("SOLO", "u1") } returns Result.success("f1")
            finish(vm.joinFamily("SOLO"))
            assertFalse(vm.promptRelationSetup.value)
            userId.value = null
            finish(vm.joinFamily("X"))
        }

    @Test
    fun `leaving reloads the state and the join code generator yields eight uppercase characters`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.leaveFamily())
            coVerify { repo.leaveFamily("u1") }
            val code = vm.generateJoinCode()
            assertEquals(8, code.length)
            assertEquals(code.uppercase(), code)
            userId.value = null
            finish(vm.leaveFamily())
            vm.consumePendingJoinCode()
        }

    @Test
    fun `renaming the family reloads and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.renameFamily("f1", "Crew") } returns Result.success(Unit)
            vm.renameFamily("Crew")
            settle { true }
            coVerify { repo.renameFamily("f1", "Crew") }
            coEvery { repo.renameFamily("f1", "Bad") } returns Result.failure(IllegalStateException("denied"))
            vm.renameFamily("Bad")
            settle { vm.error.value == "denied" }
            val empty = FamilyViewModel(app, repo)
            empty.renameFamily("Nothing")
        }

    @Test
    fun `removing a member reloads and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            coEvery { repo.removeFamilyMember("u2") } returns Result.success(Unit)
            vm.removeMember("u2")
            settle { true }
            coVerify { repo.removeFamilyMember("u2") }
            coEvery { repo.removeFamilyMember("u3") } returns Result.failure(IllegalStateException("nope"))
            vm.removeMember("u3")
            settle { vm.error.value == "nope" }
        }

    @Test
    fun `uploading a family photo stores it and updates the family`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val uri = Uri.parse("content://media/family/1")
            shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpeg()))
            backend.onJson(HttpMethod.Post, "/storage/v1/object/group-images", """{"Key":"group-images/family-photos/f1/photo.jpg","Id":"1"}""")
            coEvery { repo.updateFamilyPhoto("f1", any()) } returns Result.success(Unit)
            vm.uploadFamilyPhoto(app, uri)
            settle { !vm.isUploading.value && backend.requestsTo("storage").isNotEmpty() }
            coVerify { repo.updateFamilyPhoto("f1", match { it.contains("family-photos/f1/photo.jpg") }) }
            assertNull(vm.error.value)
        }

    @Test
    fun `an unreadable or failing photo upload reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val context = mockk<android.content.Context>(relaxed = true)
            every { context.contentResolver.openInputStream(any()) } returns null
            vm.uploadFamilyPhoto(context, Uri.parse("content://media/none"))
            settle { vm.error.value != null }
            assertEquals(app.getString(R.string.could_not_read_the_selected_photo), vm.error.value)
            vm.clearError()
            val uri = Uri.parse("content://media/family/2")
            shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpeg()))
            backend.onJson(HttpMethod.Post, "/storage/v1/object/group-images", "{}", HttpStatusCode.InternalServerError)
            vm.uploadFamilyPhoto(app, uri)
            settle { vm.error.value != null }
            assertEquals(app.getString(R.string.failed_to_update_photo_please_try_again), vm.error.value)
            FamilyViewModel(app, repo).uploadFamilyPhoto(app, uri)
        }
}
