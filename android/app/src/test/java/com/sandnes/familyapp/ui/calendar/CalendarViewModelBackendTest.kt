package com.sandnes.familyapp.ui.calendar

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.CalendarEventModel
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** CalendarViewModel against a fake Supabase backend, so the real query/decoding paths run. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelBackendTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val backend = FakeSupabase()
    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private val familyChanged = MutableSharedFlow<Unit>()

    private val mine = """{"id":"e1","user_id":"u1","family_id":"f1","activity":"Dentist","date_from":"2026-10-05","date_to":"2026-10-06"}"""
    private val foreign = """{"id":"e2","user_id":"u9","family_id":"zzz","activity":"Elsewhere","date_from":"2026-10-05","date_to":"2026-10-05"}"""
    private val personal = """{"id":"e3","user_id":"u1","activity":"Solo","date_from":"2026-10-07","date_to":""}"""

    private fun resetCompanionCache() {
        val cacheField = CalendarViewModel::class.java.getDeclaredField("cache")
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
        coEvery { repo.isFamilyAdmin(any()) } returns true
        coEvery { repo.getFamilyMembers("f1") } returns listOf(UserModel(id = "u1"), UserModel(id = "u2"))
        backend.onJson(HttpMethod.Get, "/rest/v1/calendar_events", "[$mine,$foreign,$personal]")
    }

    @After
    fun tearDown() {
        backend.uninstall()
        resetCompanionCache()
    }

    private fun settle(condition: () -> Boolean) = dispatcherRule.dispatcher.scheduler.eventually(condition = condition)

    private fun finish(job: Job) = settle { job.isCompleted }

    private fun loaded(): CalendarViewModel {
        val vm = CalendarViewModel(repo)
        userId.value = "u1"
        settle { vm.events.value.isNotEmpty() }
        return vm
    }

    private val draft =
        EventDraft(
            activity = "Party",
            allDay = false,
            dateFrom = "2026-10-10",
            dateTo = "",
            timeFrom = "18:00",
            timeTo = "22:00",
            icon = "cake",
            isPrivate = true,
            color = 5,
            attendeeIds = listOf("u2"),
        )

    @Test
    fun `loads family events members and admin flag and drops other families`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            assertEquals(listOf("e1", "e3"), vm.events.value.map { it.id })
            assertEquals(2, vm.familyMembers.value.size)
            assertTrue(vm.isAdmin.value)
            assertEquals("u1", vm.currentUserId.value)
        }

    @Test
    fun `a user without a family only sees personal events`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            val vm = loaded()
            assertEquals(listOf("e3"), vm.events.value.map { it.id })
            assertTrue(
                backend
                    .requestsTo("calendar_events")
                    .single()
                    .query
                    .contains("user_id=eq.u1"),
            )
        }

    @Test
    fun `sign out clears events and failures end loading`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            userId.value = null
            settle { vm.events.value.isEmpty() }
            backend.onJson(HttpMethod.Get, "/rest/v1/calendar_events", "{}", HttpStatusCode.InternalServerError)
            userId.value = "u1"
            settle { !vm.isLoading.value && backend.requestsTo("calendar_events").size > 1 }
        }

    @Test
    fun `selected date filters events across their range`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val collected = mutableListOf<List<String>>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    vm.eventsForSelectedDate.collect { events -> collected += events.map { it.id } }
                }
            vm.selectDate(LocalDate.of(2026, 10, 6))
            settle { collected.lastOrNull() == listOf("e1") }
            vm.selectDate(LocalDate.of(2026, 10, 7))
            settle { collected.lastOrNull() == listOf("e3") }
            vm.selectDate(LocalDate.of(2026, 12, 1))
            settle { collected.lastOrNull() == emptyList<String>() }
            assertEquals(YearMonth.of(2026, 12), vm.displayedMonth.value)
            job.cancel()
        }

    @Test
    fun `month navigation moves the displayed month`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = CalendarViewModel(repo)
            val start = vm.displayedMonth.value
            vm.nextMonth()
            assertEquals(start.plusMonths(1), vm.displayedMonth.value)
            vm.prevMonth()
            vm.prevMonth()
            assertEquals(start.minusMonths(1), vm.displayedMonth.value)
        }

    @Test
    fun `refresh and family changes reload events`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val before = backend.requestsTo("calendar_events", HttpMethod.Get).size
            finish(vm.refresh())
            familyChanged.emit(Unit)
            settle { backend.requestsTo("calendar_events", HttpMethod.Get).size >= before + 2 }
            userId.value = null
            finish(vm.refresh())
        }

    @Test
    fun `adding an event posts every field and resolves an empty end date`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.addEvent(draft))
            val body = backend.requestsTo("calendar_events", HttpMethod.Post).single().body
            assertTrue(body.contains("\"activity\":\"Party\"") && body.contains("\"date_to\":\"2026-10-10\""))
            assertTrue(body.contains("\"is_private\":true") && body.contains("\"color\":5") && body.contains("\"attendee_ids\":[\"u2\"]"))
            assertTrue(body.contains("\"family_id\":\"f1\"") && body.contains("\"time_from\":\"18:00\""))
            assertNull(vm.errorRes.value)
        }

    @Test
    fun `all-day events send blank times and personal events omit the family`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("u1") } returns UserModel(id = "u1", familyId = null)
            val vm = loaded()
            finish(vm.addEvent(draft.copy(allDay = true, color = null, dateTo = "2026-10-12")))
            val body = backend.requestsTo("calendar_events", HttpMethod.Post).single().body
            assertTrue(body.contains("\"time_from\":\"\"") && body.contains("\"time_to\":\"\""))
            assertTrue(!body.contains("family_id") && !body.contains("\"color\""))
        }

    @Test
    fun `a failed add reports an error and signed out adds are ignored`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Post, "/rest/v1/calendar_events", "{}", HttpStatusCode.InternalServerError)
            finish(vm.addEvent(draft))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            vm.clearError()
            assertNull(vm.errorRes.value)
            userId.value = null
            finish(vm.addEvent(draft))
            coEvery { repo.getUser("u1") } returns null
            userId.value = "u1"
            finish(vm.addEvent(draft))
            assertEquals(1, backend.requestsTo("calendar_events", HttpMethod.Post).size)
        }

    @Test
    fun `updating an event patches all fields with a colour or null`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            val event = vm.events.value.first()
            finish(vm.updateEvent(event.copy(activity = "Moved", color = 3, attendeeIds = listOf("u2"))))
            finish(vm.updateEvent(event.copy(color = null)))
            val patches = backend.requestsTo("calendar_events", HttpMethod.Patch).map { it.body }
            assertTrue(patches[0].contains("\"activity\":\"Moved\"") && patches[0].contains("\"color\":3"))
            assertTrue(patches[1].contains("\"color\":null"))
        }

    @Test
    fun `a failed update reports an error`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            backend.onJson(HttpMethod.Patch, "/rest/v1/calendar_events", "{}", HttpStatusCode.Forbidden)
            finish(vm.updateEvent(vm.events.value.first()))
            assertEquals(R.string.couldnt_save, vm.errorRes.value)
            userId.value = null
            finish(vm.updateEvent(CalendarEventModel(id = "x")))
        }

    @Test
    fun `deleting an event removes it and reports failures`() =
        runTest(dispatcherRule.dispatcher) {
            val vm = loaded()
            finish(vm.delete(vm.events.value.first()))
            assertTrue(
                backend
                    .requestsTo("calendar_events", HttpMethod.Delete)
                    .single()
                    .query
                    .contains("id=eq.e1"),
            )
            backend.onJson(HttpMethod.Delete, "/rest/v1/calendar_events", "{}", HttpStatusCode.Forbidden)
            finish(vm.delete(CalendarEventModel(id = "e1")))
            assertEquals(R.string.couldnt_delete, vm.errorRes.value)
            userId.value = null
            finish(vm.delete(CalendarEventModel(id = "e1")))
        }
}
