package com.example.mainactivity.ui.birthday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDate

@RunWith(JUnit4::class)
class BirthdayDateUtilsTest {
    // Fixed "today" used for all deterministic tests
    private val today = LocalDate.of(2024, 6, 25)

    // ── nextBirthdayDate ──────────────────────────────────────────────────────

    @Test
    fun nextBirthdayDate_birthdayIsToday_returnsToday() {
        // Born on June 25 in some year; today is June 25 2024
        val result = nextBirthdayDate("1990-06-25", today)
        assertEquals(LocalDate.of(2024, 6, 25), result)
    }

    @Test
    fun nextBirthdayDate_birthdayWasYesterday_returnsNextYear() {
        // June 24 in 2024 is before today (June 25), so the next occurrence is 2025
        val result = nextBirthdayDate("1990-06-24", today)
        assertEquals(LocalDate.of(2025, 6, 24), result)
    }

    @Test
    fun nextBirthdayDate_birthdayIsTomorrow_returnsThisYear() {
        // June 26 in 2024 is after today (June 25), so next occurrence is still 2024
        val result = nextBirthdayDate("1990-06-26", today)
        assertEquals(LocalDate.of(2024, 6, 26), result)
    }

    @Test
    fun nextBirthdayDate_birthdayEarlierInYear_returnsNextYear() {
        // January 1 in 2024 is before June 25, 2024 → next is January 1, 2025
        val result = nextBirthdayDate("1985-01-01", today)
        assertEquals(LocalDate.of(2025, 1, 1), result)
    }

    @Test
    fun nextBirthdayDate_birthdayLaterInYear_returnsThisYear() {
        // December 31 in 2024 is after June 25, 2024 → next is December 31, 2024
        val result = nextBirthdayDate("2000-12-31", today)
        assertEquals(LocalDate.of(2024, 12, 31), result)
    }

    @Test
    fun nextBirthdayDate_feb29InLeapYear_today2024_beforeFeb29_returnsThisYear() {
        // Feb 29, 2024 exists (2024 is a leap year). With today = 2024-02-01,
        // Feb 29 2024 > Feb 1 2024 → returns Feb 29, 2024
        val leapToday = LocalDate.of(2024, 2, 1)
        val result = nextBirthdayDate("2000-02-29", leapToday)
        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun nextBirthdayDate_feb29InNonLeapYear_returnsNextLeapYear() {
        // LocalDate.withYear adjusts Feb 29 to Feb 28 in a non-leap year (it does not throw),
        // so 2023-02-28 is already past 2023-06-25 → the next actual Feb 29 lands in leap 2024.
        val nonLeapToday = LocalDate.of(2023, 6, 25)
        val result = nextBirthdayDate("2000-02-29", nonLeapToday)
        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun nextBirthdayDate_invalidDateString_returnsNull() {
        assertNull(nextBirthdayDate("not-a-date", today))
    }

    @Test
    fun nextBirthdayDate_emptyString_returnsNull() {
        assertNull(nextBirthdayDate("", today))
    }

    // ── turnsAge ──────────────────────────────────────────────────────────────

    @Test
    fun turnsAge_born1990Jan01_todayIsJan012024_returns34() {
        // next birthday = Jan 1, 2024 (not before today = Jan 1, 2024)
        // age = 2024 - 1990 = 34
        val todayJan = LocalDate.of(2024, 1, 1)
        val result = turnsAge("1990-01-01", todayJan)
        assertEquals(34, result)
    }

    @Test
    fun turnsAge_born1990Jan02_todayIsJan012024_returns34() {
        // next birthday = Jan 2, 2024 (Jan 2 >= Jan 1, so same year)
        // age = 2024 - 1990 = 34
        val todayJan = LocalDate.of(2024, 1, 1)
        val result = turnsAge("1990-01-02", todayJan)
        assertEquals(34, result)
    }

    @Test
    fun turnsAge_birthdayWasYesterday_returnsNextYearAge() {
        // June 24 is before today (June 25), so next is June 24, 2025
        // age = 2025 - 1990 = 35
        val result = turnsAge("1990-06-24", today)
        assertEquals(35, result)
    }

    @Test
    fun turnsAge_birthdayIsToday_returnsCurrentAge() {
        // June 25 == today → next = June 25, 2024
        // age = 2024 - 1990 = 34
        val result = turnsAge("1990-06-25", today)
        assertEquals(34, result)
    }

    @Test
    fun turnsAge_invalidDateString_returnsNull() {
        assertNull(turnsAge("bad-date", today))
    }

    @Test
    fun turnsAge_emptyString_returnsNull() {
        assertNull(turnsAge("", today))
    }

    @Test
    fun turnsAge_resultIsNonNegative() {
        val result = turnsAge("1990-12-31", today)
        assertNotNull(result)
        assertTrue(result!! >= 0)
    }
}
