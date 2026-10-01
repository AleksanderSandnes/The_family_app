package com.sandnes.familyapp.ui.calendar

import com.sandnes.familyapp.R
import com.sandnes.familyapp.data.UserModel
import com.sandnes.familyapp.testutil.ComposeScreenTest
import com.sandnes.familyapp.testutil.clickDescription
import com.sandnes.familyapp.testutil.clickText
import com.sandnes.familyapp.testutil.clickToggle
import com.sandnes.familyapp.testutil.hasText
import com.sandnes.familyapp.testutil.runSwipeDelete
import com.sandnes.familyapp.testutil.str
import com.sandnes.familyapp.testutil.typeInto
import com.sandnes.familyapp.testutil.waitForText
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarScreenTest : ComposeScreenTest() {
    private val today: LocalDate = LocalDate.now()
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    @Suppress("LongParameterList")
    private fun event(
        id: String,
        owner: String,
        name: String,
        from: LocalDate = today,
        to: LocalDate = from,
        extra: String = "",
    ) = """{"id":"$id","user_id":"$owner","family_id":"f1","activity":"$name","date_from":"$from","date_to":"$to","time_from":"14:00","time_to":"15:30"$extra}"""

    private val events =
        listOf(
            event("e1", "u1", "Dentist"),
            event("e2", "u2", "Family party", extra = ""","all_day":true,"color":255,"attendee_ids":["u2","u3","u4"]"""),
            event("e3", "u2", "Secret plan", extra = ""","is_private":true"""),
            event("e4", "u1", "Trip", from = today, to = today.plusDays(3), extra = ""","icon":"flight""""),
            event("e5", "u1", "Next month", from = today.plusDays(40)),
            event("e6", "u1", "Long gone", from = today.minusDays(40)),
        ).joinToString(prefix = "[", postfix = "]", separator = ",")

    @Before
    fun reset() = resetCache(CalendarViewModel::class.java)

    @After
    fun clear() = resetCache(CalendarViewModel::class.java)

    private fun viewModel(
        json: String = events,
        admin: Boolean = true,
    ): CalendarViewModel {
        backend.onJson(HttpMethod.Get, "/rest/v1/calendar_events", json)
        val repo =
            fakeRepo(admin = admin) {
                coEvery { getFamilyMembers("f1") } returns
                    listOf(
                        UserModel(id = "u1", name = "Ada"),
                        UserModel(id = "u2", name = "Bob", avatarColor = 0xFF00AA00.toInt()),
                        UserModel(id = "u3", name = "Cy"),
                        UserModel(id = "u4", name = "Di"),
                    )
            }
        return CalendarViewModel(repo)
    }

    @Test
    fun `month view lists the selected day's events and switches views`() {
        val vm = viewModel()
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText("Dentist")
        assertTrue(compose.hasText("Family party"))
        assertTrue(compose.hasText(str(R.string.all_day)))
        // Month navigation and the Today shortcut.
        compose.clickDescription(str(R.string.next_month))
        compose.clickDescription(str(R.string.previous_month))
        compose.clickDescription(str(R.string.previous_month))
        compose.clickText(str(R.string.today))
        // Week and agenda views.
        compose.clickText(str(R.string.week))
        compose.waitForText("Dentist")
        compose.clickText(str(R.string.agenda))
        compose.waitForText("Next month")
        assertTrue(!compose.hasText("Long gone"))
        compose.clickText(str(R.string.month))
        compose.waitForText("Dentist")
    }

    @Test
    fun `selecting a day without events shows the empty state and agenda can be empty`() {
        val vm = viewModel(json = "[]")
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText(str(R.string.no_events))
        compose.clickText(str(R.string.agenda))
        compose.waitForText(str(R.string.nothing_coming_up))
    }

    @Test
    fun `adding an event fills the sheet and posts it`() {
        val vm = viewModel(json = "[]")
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText(str(R.string.no_events))
        compose.clickDescription(str(R.string.add_new_calendar_event))
        compose.waitForText(str(R.string.going_with).uppercase())
        compose.typeInto("Picnic")
        compose.clickToggle(0) // private
        compose.clickToggle(1) // all day
        compose.clickToggle(1) // back to timed
        compose.clickText(today.format(dayFmt), index = 0)
        compose.clickText(str(R.string.ok))
        compose.clickText(today.format(dayFmt), index = 1)
        compose.clickText(str(R.string.ok))
        compose.clickText("Bob")
        compose.clickText("Bob")
        compose.clickText("Cy")
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/calendar_events", HttpMethod.Post).isNotEmpty() }
        assertTrue(
            backend
                .requestsTo("/rest/v1/calendar_events", HttpMethod.Post)
                .first()
                .body
                .contains("Picnic"),
        )
    }

    @Test
    fun `time pickers open and confirm`() {
        val vm = viewModel(json = "[]")
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText(str(R.string.no_events))
        compose.clickDescription(str(R.string.add_new_calendar_event))
        compose.waitForText(str(R.string.starts))
        compose.clickText(str(R.string.start_time).let { "9:00 AM" })
        compose.waitForText(str(R.string.start_time))
        compose.clickText(str(R.string.ok))
        compose.clickText("10:00 AM")
        compose.waitForText(str(R.string.end_time))
        compose.clickText(str(R.string.cancel))
    }

    @Test
    fun `tapping an event edits it and a creator or admin can delete`() {
        val vm = viewModel()
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText("Dentist")
        compose.clickText("Dentist")
        compose.waitForText(str(R.string.edit_event))
        compose.typeInto("!")
        compose.clickText(str(R.string.save))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/calendar_events", HttpMethod.Patch).isNotEmpty() }
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_event_q))
        compose.clickText(str(R.string.cancel))
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_event_q))
        compose.clickText(str(R.string.delete))
        compose.waitUntil(20_000) { backend.requestsTo("/rest/v1/calendar_events", HttpMethod.Delete).isNotEmpty() }
    }

    @Test
    fun `agenda groups events by date and allows deleting from it`() {
        val vm = viewModel()
        compose.setContent { CalendarScreen(viewModel = vm) }
        compose.waitForText("Dentist")
        compose.clickText(str(R.string.agenda))
        compose.waitForText("Trip")
        compose.runSwipeDelete()
        compose.waitForText(str(R.string.delete_event_q))
        compose.clickText(str(R.string.cancel))
        compose.clickText("Trip")
        compose.waitForText(str(R.string.edit_event))
    }
}
