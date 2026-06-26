package com.example.mainactivity.ui.calendar

import app.cash.turbine.test
import com.example.mainactivity.data.CalendarEventModel
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.UserModel
import com.example.mainactivity.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for [CalendarViewModel].
 *
 * CalendarViewModel calls SupabaseManager.client directly for all DB and Realtime operations.
 * Those calls are wrapped in runCatching inside loadEvents() so they fail silently in a
 * plain unit-test environment (no real Supabase connection). The tests therefore focus on:
 *   - Initial StateFlow values before any coroutine runs
 *   - Pure navigation logic (selectDate, nextMonth, prevMonth)
 *   - eventsForSelectedDate filtering (collected via Turbine — the flow uses WhileSubscribed)
 *   - Optimistic mutations (addEvent / updateEvent / delete) applied to the events StateFlow
 *   - Lifecycle triggers (userId flow, familyChanged flow, refresh()) that initiate reload
 *
 * Tests deliberately use UserModel(familyId = null) to prevent subscribeToEvents() from
 * being reached; that function contains SupabaseManager.client.channel() and
 * channel.subscribe() calls that are NOT wrapped in runCatching.
 *
 * No mockkObject(SupabaseManager) is needed: the real lazy client's calls fail inside
 * runCatching, leaving optimistic state intact — the same proven pattern as BirthdayViewModelTest.
 */
@RunWith(JUnit4::class)
class CalendarViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var repo: FamilyRepository
    private lateinit var userId: MutableStateFlow<String?>
    private lateinit var familyChanged: MutableSharedFlow<Unit>
    private lateinit var vm: CalendarViewModel

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        userId = MutableStateFlow(null)
        familyChanged = MutableSharedFlow()
        every { repo.currentUserId } returns userId
        every { repo.familyChanged } returns familyChanged
        // Default: getUser returns null so loadEvents() skips the DB branch entirely
        coEvery { repo.getUser(any()) } returns null
        vm = CalendarViewModel(repo)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: seed _events directly via reflection to avoid System.currentTimeMillis()
    // collisions and to bypass the DB layer for tests that need pre-loaded state.
    // ─────────────────────────────────────────────────────────────────────────

    private fun seedEvents(vararg events: CalendarEventModel) {
        val field = CalendarViewModel::class.java.getDeclaredField("_events")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<List<CalendarEventModel>>).value = events.toList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Initial state — before any coroutine is advanced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial events list is empty before coroutines run`() {
        assertTrue("events must start empty", vm.events.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false before coroutines run`() {
        assertFalse("isLoading must start false", vm.isLoading.value)
    }

    @Test
    fun `initial selectedDate is today`() {
        assertEquals("selectedDate should be today", LocalDate.now(), vm.selectedDate.value)
    }

    @Test
    fun `initial displayedMonth is current month`() {
        assertEquals("displayedMonth should be current month", YearMonth.now(), vm.displayedMonth.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Pure navigation logic — no coroutines, no repo calls
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `selectDate updates selectedDate`() {
        val target = LocalDate.of(2025, 3, 15)
        vm.selectDate(target)
        assertEquals("selectedDate should reflect the chosen date", target, vm.selectedDate.value)
    }

    @Test
    fun `selectDate also updates displayedMonth to match the chosen date`() {
        val target = LocalDate.of(2025, 3, 15)
        vm.selectDate(target)
        assertEquals(
            "displayedMonth should match the year-month of the chosen date",
            YearMonth.of(2025, 3),
            vm.displayedMonth.value,
        )
    }

    @Test
    fun `nextMonth advances displayedMonth by one month`() {
        val before = vm.displayedMonth.value
        vm.nextMonth()
        assertEquals(
            "displayedMonth should advance by 1",
            before.plusMonths(1),
            vm.displayedMonth.value,
        )
    }

    @Test
    fun `prevMonth decrements displayedMonth by one month`() {
        val before = vm.displayedMonth.value
        vm.prevMonth()
        assertEquals(
            "displayedMonth should decrease by 1",
            before.minusMonths(1),
            vm.displayedMonth.value,
        )
    }

    @Test
    fun `nextMonth then prevMonth returns to original month`() {
        val original = vm.displayedMonth.value
        vm.nextMonth()
        vm.prevMonth()
        assertEquals("Round-trip should return to original month", original, vm.displayedMonth.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. eventsForSelectedDate filtering
    //    Must collect via Turbine because the Flow uses WhileSubscribed(5000):
    //    reading .value without an active subscriber returns the initial emptyList().
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `eventsForSelectedDate returns event whose date range covers selected date`() =
        runTest(dispatcherRule.dispatcher) {
            val today = LocalDate.now()
            val event =
                CalendarEventModel(
                    id = "e1",
                    userId = "u1",
                    dateFrom = today.toString(),
                    dateTo = today.toString(),
                    activity = "Daily standup",
                )
            seedEvents(event)
            vm.selectDate(today)

            vm.eventsForSelectedDate.test {
                advanceUntilIdle()
                val result = expectMostRecentItem()
                assertEquals("Event on the selected date must be included", 1, result.size)
                assertEquals("e1", result[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eventsForSelectedDate excludes event on a different date`() =
        runTest(dispatcherRule.dispatcher) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val event =
                CalendarEventModel(
                    id = "e2",
                    userId = "u1",
                    dateFrom = yesterday.toString(),
                    dateTo = yesterday.toString(),
                    activity = "Yesterday meeting",
                )
            seedEvents(event)
            vm.selectDate(today)

            vm.eventsForSelectedDate.test {
                advanceUntilIdle()
                val result = expectMostRecentItem()
                assertTrue("Event from yesterday must not appear on today", result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eventsForSelectedDate includes multi-day event that spans selected date`() =
        runTest(dispatcherRule.dispatcher) {
            val today = LocalDate.now()
            val event =
                CalendarEventModel(
                    id = "e3",
                    userId = "u1",
                    dateFrom = today.minusDays(2).toString(),
                    dateTo = today.plusDays(2).toString(),
                    activity = "Week event",
                )
            seedEvents(event)
            vm.selectDate(today)

            vm.eventsForSelectedDate.test {
                advanceUntilIdle()
                val result = expectMostRecentItem()
                assertEquals(
                    "Multi-day event must be included when it spans the selected date",
                    1,
                    result.size,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eventsForSelectedDate filters correctly when multiple events are present`() =
        runTest(dispatcherRule.dispatcher) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val todayEvent =
                CalendarEventModel(
                    id = "e-today",
                    userId = "u1",
                    dateFrom = today.toString(),
                    dateTo = today.toString(),
                    activity = "Today",
                )
            val tomorrowEvent =
                CalendarEventModel(
                    id = "e-tomorrow",
                    userId = "u1",
                    dateFrom = tomorrow.toString(),
                    dateTo = tomorrow.toString(),
                    activity = "Tomorrow",
                )
            seedEvents(todayEvent, tomorrowEvent)
            vm.selectDate(today)

            vm.eventsForSelectedDate.test {
                advanceUntilIdle()
                val result = expectMostRecentItem()
                assertEquals("Only today's event should appear", 1, result.size)
                assertEquals("e-today", result[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Null userId — idle state, no load attempted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `null userId keeps events empty and isLoading false`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            assertTrue("events should be empty when userId is null", vm.events.value.isEmpty())
            assertFalse("isLoading should be false after null userId", vm.isLoading.value)
        }

    @Test
    fun `null userId does not trigger getUser`() =
        runTest(dispatcherRule.dispatcher) {
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    @Test
    fun `userId becoming null after non-null clears events`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            userId.value = null
            advanceUntilIdle()

            assertTrue(
                "events should be cleared when userId becomes null",
                vm.events.value.isEmpty(),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Non-null userId — load is triggered
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `non-null userId triggers at least one getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            coVerify(atLeast = 1) { repo.getUser("user-1") }
        }

    @Test
    fun `getUser returning null leaves events empty and resets isLoading`() =
        runTest(dispatcherRule.dispatcher) {
            // Default setup: getUser returns null
            userId.value = "user-1"
            advanceUntilIdle()

            assertTrue(
                "events should remain empty when getUser returns null",
                vm.events.value.isEmpty(),
            )
            assertFalse("isLoading should be reset to false", vm.isLoading.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. addEvent — optimistic item appears in the list
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addEvent creates optimistic item with correct fields`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.addEvent(
                EventDraft(
                    activity = "Team lunch",
                    allDay = false,
                    dateFrom = "2025-07-10",
                    dateTo = "2025-07-10",
                    timeFrom = "12:00",
                    timeTo = "13:00",
                ),
            )
            advanceUntilIdle()

            val items = vm.events.value
            assertEquals("Should have exactly one event", 1, items.size)
            assertEquals("activity should match", "Team lunch", items[0].activity)
            assertEquals("dateFrom should match", "2025-07-10", items[0].dateFrom)
            assertEquals("dateTo should match", "2025-07-10", items[0].dateTo)
        }

    @Test
    fun `addEvent uses dateFrom as dateTo when dateTo is blank`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.addEvent(
                EventDraft(
                    activity = "Solo day",
                    allDay = true,
                    dateFrom = "2025-07-15",
                    dateTo = "",
                    timeFrom = "",
                    timeTo = "",
                ),
            )
            advanceUntilIdle()

            val item = vm.events.value.first()
            assertEquals(
                "dateTo should equal dateFrom when blank",
                "2025-07-15",
                item.dateTo,
            )
        }

    @Test
    fun `addEvent when getUser returns null does not modify list`() =
        runTest(dispatcherRule.dispatcher) {
            // Default: getUser returns null → addEvent hits early return guard
            userId.value = "user-1"
            advanceUntilIdle()

            vm.addEvent(
                EventDraft(
                    activity = "Ghost event",
                    allDay = false,
                    dateFrom = "2025-07-10",
                    dateTo = "2025-07-10",
                    timeFrom = "",
                    timeTo = "",
                ),
            )
            advanceUntilIdle()

            assertTrue(
                "events must stay empty when getUser returns null",
                vm.events.value.isEmpty(),
            )
        }

    @Test
    fun `addEvent sets allDay flag and clears time fields for all-day events`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user
            userId.value = "user-1"
            advanceUntilIdle()

            vm.addEvent(
                EventDraft(
                    activity = "Holiday",
                    allDay = true,
                    dateFrom = "2025-08-01",
                    dateTo = "2025-08-01",
                    timeFrom = "09:00",
                    timeTo = "17:00",
                ),
            )
            advanceUntilIdle()

            val item = vm.events.value.first()
            assertTrue("allDay flag should be true", item.allDay)
            assertEquals("timeFrom should be empty for allDay event", "", item.timeFrom)
            assertEquals("timeTo should be empty for allDay event", "", item.timeTo)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. delete() — item removed optimistically
    //    Events are seeded via reflection to avoid System.currentTimeMillis() collisions.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes the targeted event from list`() =
        runTest(dispatcherRule.dispatcher) {
            val event = CalendarEventModel(id = "del-1", userId = "u1", activity = "ToDelete")
            seedEvents(event)

            vm.delete(event)
            advanceUntilIdle()

            assertTrue(
                "events should be empty after deleting the only item",
                vm.events.value.isEmpty(),
            )
        }

    @Test
    fun `delete only removes the targeted item leaving others intact`() =
        runTest(dispatcherRule.dispatcher) {
            val eventA = CalendarEventModel(id = "del-A", userId = "u1", activity = "Alpha")
            val eventB = CalendarEventModel(id = "del-B", userId = "u1", activity = "Beta")
            seedEvents(eventA, eventB)

            vm.delete(eventA)
            advanceUntilIdle()

            val remaining = vm.events.value
            assertEquals("Only one event should remain", 1, remaining.size)
            assertEquals("Remaining event should be Beta", "Beta", remaining[0].activity)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. updateEvent() — optimistic change reflected in the list
    //    Events are seeded via reflection to ensure stable IDs.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `updateEvent applies optimistic activity change`() =
        runTest(dispatcherRule.dispatcher) {
            val original =
                CalendarEventModel(
                    id = "upd-1",
                    userId = "u1",
                    activity = "Original activity",
                    dateFrom = "2025-07-01",
                    dateTo = "2025-07-01",
                )
            seedEvents(original)

            val updated = original.copy(activity = "Updated activity")
            vm.updateEvent(updated)
            advanceUntilIdle()

            val item = vm.events.value.first()
            assertEquals("activity should be updated", "Updated activity", item.activity)
        }

    @Test
    fun `updateEvent does not modify non-matching items`() =
        runTest(dispatcherRule.dispatcher) {
            val eventA = CalendarEventModel(id = "upd-A", userId = "u1", activity = "Alpha")
            val eventB = CalendarEventModel(id = "upd-B", userId = "u1", activity = "Beta")
            seedEvents(eventA, eventB)

            val updatedA = eventA.copy(activity = "Alpha-Renamed")
            vm.updateEvent(updatedA)
            advanceUntilIdle()

            val beta = vm.events.value.first { it.id == "upd-B" }
            assertEquals("Beta's activity should be unchanged", "Beta", beta.activity)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Error handling — getUser throws; no crash, events stay empty, isLoading resets
    //    CalendarViewModel has no explicit error state: loadEvents() swallows all
    //    exceptions via runCatching and only resets isLoading on exit.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getUser throwing does not crash the ViewModel and events stay empty`() =
        runTest(dispatcherRule.dispatcher) {
            coEvery { repo.getUser("user-1") } throws RuntimeException("network error")

            userId.value = "user-1"
            advanceUntilIdle()

            assertTrue("events should be empty after exception", vm.events.value.isEmpty())
            assertFalse("isLoading should be false after exception", vm.isLoading.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. familyChanged — triggers a reload (getUser called again)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `familyChanged emission triggers an additional getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()
            // At least 1 getUser call happened during the initial load

            familyChanged.emit(Unit)
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. refresh() — forces a reload outside of the Flow collectors
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `refresh triggers another getUser call`() =
        runTest(dispatcherRule.dispatcher) {
            userId.value = "user-1"
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            coVerify(atLeast = 2) { repo.getUser("user-1") }
        }

    @Test
    fun `refresh with null userId does not call getUser`() =
        runTest(dispatcherRule.dispatcher) {
            // userId stays null
            vm.refresh()
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.getUser(any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Turbine — events Flow emits expected sequence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `events flow emits empty then populated after addEvent via turbine`() =
        runTest(dispatcherRule.dispatcher) {
            val user = UserModel(id = "user-1", familyId = null)
            coEvery { repo.getUser("user-1") } returns user

            vm.events.test {
                val initial = awaitItem()
                assertTrue("Initial emission should be empty", initial.isEmpty())

                userId.value = "user-1"
                vm.addEvent(
                    EventDraft(
                        activity = "Turbine event",
                        allDay = false,
                        dateFrom = "2025-09-01",
                        dateTo = "2025-09-01",
                        timeFrom = "10:00",
                        timeTo = "11:00",
                    ),
                )
                advanceUntilIdle()

                // The optimistic add emits a new list; reload() fails silently so the
                // item persists as the most recent emitted value.
                val populated = expectMostRecentItem()
                assertTrue(
                    "Flow should contain the added event",
                    populated.any { it.activity == "Turbine event" },
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}
