package com.sandnes.familyapp.ui.birthday

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.BirthdayModel
import com.sandnes.familyapp.data.FamilyRepository
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.FakeSupabase
import com.sandnes.familyapp.testutil.eventually
import com.sandnes.familyapp.util.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** BirthdayViewModel against a fake Supabase backend, so the real query/decoding paths run. */
class BirthdayViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val familyChanged = MutableSharedFlow<Unit>()

    private fun resetCompanionCache() {
        val cacheField = BirthdayViewModel::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        cacheField.set(null, emptyList<Any?>())
    }

    @Before
    fun setUp() {
        backend.install()
        resetCompanionCache()
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        coEvery { repo.getUser("u1") } returns UserModel(id = "u1", name = "Ada", familyId = "f1")
        backend.onJson(
            HttpMethod.Get,
            "/rest/v1/birthdays",
            """[{"id":"b1","name":"Bob","date":"1990-01-01","family_id":"f1","made_by_user_id":"u1"},
                {"id":"b2","name":"Other","date":"1990-02-02","family_id":"zzz"}]""",
        )
    }

    @After
    fun tearDown() {
        backend.uninstall()
        resetCompanionCache()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): BirthdayViewModel {
        val vm = BirthdayViewModel(repo)
        userId.value = "u1"
        settle { vm.birthdays.value.isNotEmpty() }
        return vm
    }

    @Test
    fun `loads family birthdays and drops other families`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals(listOf("b1"), vm.birthdays.value.map { it.id })
            assertEquals("u1", vm.currentUserId.value)
        }

    @Test
    fun `a user without a family sees the birthdays they made`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", """[{"id":"b1","name":"Mine","made_by_user_id":"u1"}]""")
            val vm = loaded()
            assertEquals(listOf("b1"), vm.birthdays.value.map { it.id })
            assertTrue(
                backend
                    .requestsTo("birthdays")
                    .single()
                    .query
                    .contains("made_by_user_id=eq.u1"),
            )
        }

    @Test
    fun `sign out clears and a missing profile or failed fetch leaves no data`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            userId.value = null
            settle { vm.birthdays.value.isEmpty() }
            coEvery { repo.getUser("u1") } returns null
            userId.value = "u1"
            settle { !vm.isLoading.value }
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = "f1")
            backend.onJson(HttpMethod.Get, "/rest/v1/birthdays", "{}", HttpStatusCode.InternalServerError)
            finish(vm.refresh())
            settle { !vm.isLoading.value }
        }

    @Test
    fun `refresh and family changes reload birthdays`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("birthdays", HttpMethod.Get).size
            finish(vm.refresh())
            familyChanged.emit(Unit)
            settle { backend.requestsTo("birthdays", HttpMethod.Get).size >= before + 2 }
            userId.value = null
            finish(vm.refresh())
            familyChanged.emit(Unit)
        }

    @Test
    fun `adding a birthday posts family owner icon and optional colour`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.add("Cat", "2000-03-03", "cake", 4))
            val body = backend.requestsTo("birthdays", HttpMethod.Post).single().body
            assertTrue(body.contains("\"name\":\"Cat\"") && body.contains("\"family_id\":\"f1\"") && body.contains("\"color\":4"))
            assertTrue(body.contains("\"made_by_user_id\":\"u1\"") && body.contains("\"icon\":\"cake\""))
        }

    @Test
    fun `adding without a family or colour omits them and failures are reported`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            val vm = loaded()
            finish(vm.add("Solo", "2000-03-03", "cake", null))
            val body = backend.requestsTo("birthdays", HttpMethod.Post).single().body
            assertTrue(!body.contains("family_id") && !body.contains("\"color\""))
            backend.onJson(HttpMethod.Post, "/rest/v1/birthdays", "{}", HttpStatusCode.InternalServerError)
            finish(vm.add("Broken", "2000-03-03", "cake", null))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `adding is ignored while signed out or without a profile`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = BirthdayViewModel(repo)
            finish(vm.add("Nope", "2000-01-01", "cake", null))
            coEvery { repo.getUser("u1") } returns null
            userId.value = "u1"
            finish(vm.add("Nope", "2000-01-01", "cake", null))
            assertTrue(backend.requestsTo("birthdays", HttpMethod.Post).isEmpty())
        }

    @Test
    fun `updating patches the row with a colour or null`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.update("b1", "Bobby", "1990-01-02", "star", 8))
            finish(vm.update("b1", "Bobby", "1990-01-02", "star", null))
            val patches = backend.requestsTo("birthdays", HttpMethod.Patch).map { it.body }
            assertTrue(patches[0].contains("\"name\":\"Bobby\"") && patches[0].contains("\"color\":8"))
            assertTrue(patches[1].contains("\"color\":null"))
            backend.onJson(HttpMethod.Patch, "/rest/v1/birthdays", "{}", HttpStatusCode.Forbidden)
            finish(vm.update("b1", "x", "1990-01-01", "star", null))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
        }

    @Test
    fun `updating while signed out only changes the local list`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = BirthdayViewModel(repo)
            finish(vm.update("b1", "x", "1990-01-01", "star", null))
        }

    @Test
    fun `deleting removes the birthday and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.delete(BirthdayModel(id = "b1")))
            assertTrue(
                backend
                    .requestsTo("birthdays", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.b1"),
            )
            backend.onJson(HttpMethod.Delete, "/rest/v1/birthdays", "{}", HttpStatusCode.Forbidden)
            finish(vm.delete(BirthdayModel(id = "b1")))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
            userId.value = null
            finish(vm.delete(BirthdayModel(id = "b1")))
        }
}
