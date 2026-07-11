package com.sandnes.familyapp.ui.home

import com.sandnes.familyapp.data.BirthdayModel
import com.sandnes.familyapp.data.CalendarEventModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDate

/**
 * Unit tests for the pure Home-dashboard summary helpers ([eventHasEnded], [minutesSinceMidnight],
 * [eventWhen], [birthdayWhen]). These drive which glanceable cards appear and how they read.
 */
@RunWith(JUnit4::class)
class HomeSummaryUtilsTest {
    private val today = LocalDate.of(2026, 7, 10)

    /** English labels, as the UI would resolve them from resources. */
    private val labels =
        SummaryLabels(
            today = "Today",
            tomorrow = "Tomorrow",
            todayExclaim = "Today!",
            inDaysFormat = "in %1\$d days",
            turnsFormat = "Turns %1\$d",
        )

    // ── minutesSinceMidnight ─────────────────────────────────────────────────

    @Test
    fun `minutesSinceMidnight parses valid times`() {
        assertEquals(0, minutesSinceMidnight("00:00"))
        assertEquals(9 * 60 + 30, minutesSinceMidnight("09:30"))
        assertEquals(23 * 60 + 59, minutesSinceMidnight("23:59"))
    }

    @Test
    fun `minutesSinceMidnight rejects invalid input`() {
        assertNull(minutesSinceMidnight(""))
        assertNull(minutesSinceMidnight("24:00"))
        assertNull(minutesSinceMidnight("12:60"))
        assertNull(minutesSinceMidnight("noon"))
        assertNull(minutesSinceMidnight("12"))
    }

    // ── eventHasEnded ────────────────────────────────────────────────────────

    private fun event(
        from: String,
        to: String = "",
        timeFrom: String = "",
        timeTo: String = "",
        allDay: Boolean = false,
    ) = CalendarEventModel(
        dateFrom = from,
        dateTo = to,
        timeFrom = timeFrom,
        timeTo = timeTo,
        allDay = allDay,
        activity = "x",
    )

    @Test
    fun `event on a past day has ended`() {
        assertTrue(eventHasEnded(event(from = "2026-07-09"), today, nowMinutes = 600))
    }

    @Test
    fun `event on a future day has not ended`() {
        assertFalse(eventHasEnded(event(from = "2026-07-11"), today, nowMinutes = 600))
    }

    @Test
    fun `timed event earlier today has ended`() {
        // ends 09:00, now 10:00
        assertTrue(eventHasEnded(event(from = "2026-07-10", timeTo = "09:00"), today, nowMinutes = 600))
    }

    @Test
    fun `timed event later today has not ended`() {
        // ends 18:00, now 10:00
        assertFalse(eventHasEnded(event(from = "2026-07-10", timeTo = "18:00"), today, nowMinutes = 600))
    }

    @Test
    fun `all-day event today stays visible until the day rolls over`() {
        assertFalse(eventHasEnded(event(from = "2026-07-10", allDay = true), today, nowMinutes = 1439))
    }

    @Test
    fun `event with no end time stays visible today`() {
        assertFalse(eventHasEnded(event(from = "2026-07-10"), today, nowMinutes = 1439))
    }

    @Test
    fun `multi-day event uses its end date`() {
        val e = event(from = "2026-07-08", to = "2026-07-12")
        assertFalse(eventHasEnded(e, today, nowMinutes = 600))
    }

    @Test
    fun `unparseable date is treated as not ended`() {
        assertFalse(eventHasEnded(event(from = "not-a-date"), today, nowMinutes = 600))
    }

    // ── eventWhen ────────────────────────────────────────────────────────────

    @Test
    fun `eventWhen labels today and tomorrow`() {
        assertEquals("Today", eventWhen(event(from = "2026-07-10", allDay = true), today, labels))
        assertEquals("Tomorrow", eventWhen(event(from = "2026-07-11", allDay = true), today, labels))
    }

    @Test
    fun `eventWhen appends time for timed events`() {
        assertEquals("Today · 14:00", eventWhen(event(from = "2026-07-10", timeFrom = "14:00"), today, labels))
    }

    // ── birthdayWhen ─────────────────────────────────────────────────────────

    @Test
    fun `birthdayWhen reads today tomorrow and in-N-days`() {
        val b = BirthdayModel(name = "Sam", date = "1990-07-10")
        assertTrue(birthdayWhen(b, today, today, labels).contains("Today!"))
        assertTrue(birthdayWhen(b, today.plusDays(1), today, labels).contains("Tomorrow"))
        assertTrue(birthdayWhen(b, today.plusDays(3), today, labels).contains("in 3 days"))
    }

    @Test
    fun `birthdayWhen includes turning age when derivable`() {
        val b = BirthdayModel(name = "Sam", date = "1990-07-10")
        assertTrue(birthdayWhen(b, today, today, labels).startsWith("Turns 36"))
    }

    @Test
    fun `birthdayWhen uses localized labels`() {
        val nb =
            SummaryLabels(
                today = "I dag",
                tomorrow = "I morgen",
                todayExclaim = "I dag!",
                inDaysFormat = "om %1\$d dager",
                turnsFormat = "Fyller %1\$d",
            )
        val b = BirthdayModel(name = "Sam", date = "1990-07-10")
        assertEquals("Fyller 36 · I dag!", birthdayWhen(b, today, today, nb))
        assertTrue(birthdayWhen(b, today.plusDays(3), today, nb).contains("om 3 dager"))
    }
}
