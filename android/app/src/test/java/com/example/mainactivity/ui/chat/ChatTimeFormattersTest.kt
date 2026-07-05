package com.example.mainactivity.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant

@RunWith(JUnit4::class)
class ChatTimeFormattersTest {
    // ── relativeTime ──────────────────────────────────────────────────────────

    @Test
    fun relativeTime_30SecondsAgo_returnsNow() {
        val iso = Instant.now().minusSeconds(30).toString()
        assertEquals("now", relativeTime(iso))
    }

    @Test
    fun relativeTime_5MinutesAgo_returns5mAgo() {
        val iso = Instant.now().minusSeconds(5 * 60).toString()
        assertEquals("5m ago", relativeTime(iso))
    }

    @Test
    fun relativeTime_59MinutesAgo_returns59mAgo() {
        val iso = Instant.now().minusSeconds(59 * 60).toString()
        assertEquals("59m ago", relativeTime(iso))
    }

    @Test
    fun relativeTime_2HoursAgo_returns2hAgo() {
        val iso = Instant.now().minusSeconds(2 * 3600).toString()
        assertEquals("2h ago", relativeTime(iso))
    }

    @Test
    fun relativeTime_23HoursAgo_returns23hAgo() {
        val iso = Instant.now().minusSeconds(23 * 3600).toString()
        assertEquals("23h ago", relativeTime(iso))
    }

    @Test
    fun relativeTime_yesterday_returnsYesterday() {
        // 25 hours back: diffMs / 86_400_000 == 1L
        val iso = Instant.now().minusSeconds(25 * 3600).toString()
        assertEquals("Yesterday", relativeTime(iso))
    }

    @Test
    fun relativeTime_3DaysAgo_returnsDayAbbreviation() {
        val iso = Instant.now().minusSeconds(3L * 24 * 3600).toString()
        val result = relativeTime(iso)
        // Expected: short day-of-week name like "Mon", "Tue", etc.
        assertTrue("Expected a short day name, got: $result", result.isNotEmpty())
        assertFalse("Should not contain 'ago'", result.contains("ago"))
        assertFalse("Should not be 'Yesterday'", result == "Yesterday")
    }

    @Test
    fun relativeTime_10DaysAgo_returnsFormattedMonthDay() {
        val iso = Instant.now().minusSeconds(10L * 24 * 3600).toString()
        val result = relativeTime(iso)
        // Expected: "Mon dd" e.g. "Jun 15"
        assertTrue("Expected a formatted date, got: $result", result.isNotEmpty())
        assertFalse("Should not contain 'ago'", result.contains("ago"))
        assertFalse("Should not be 'Yesterday'", result == "Yesterday")
    }

    @Test
    fun relativeTime_invalidString_returnsEmpty() {
        assertEquals("", relativeTime("not-a-timestamp"))
    }

    @Test
    fun relativeTime_emptyString_returnsEmpty() {
        assertEquals("", relativeTime(""))
    }

    // ── messageTimeLabel ──────────────────────────────────────────────────────

    @Test
    fun messageTimeLabel_sameDay_returnsTimeOnly() {
        val iso = Instant.now().minusSeconds(5 * 60).toString()
        val result = messageTimeLabel(iso)
        // Format is "h:mm a" — must contain AM or PM, no day prefix
        assertTrue("Should not be empty", result.isNotEmpty())
        assertTrue("Should contain AM or PM", result.contains("AM") || result.contains("PM"))
        assertFalse("Should not contain 'Yesterday'", result.contains("Yesterday"))
    }

    @Test
    fun messageTimeLabel_yesterday_startsWithYesterday() {
        // 25 h ago: diffD == 1L → "Yesterday HH:mm"
        val iso = Instant.now().minusSeconds(25 * 3600).toString()
        val result = messageTimeLabel(iso)
        assertTrue("Expected 'Yesterday' prefix, got: $result", result.startsWith("Yesterday"))
    }

    @Test
    fun messageTimeLabel_3DaysAgo_containsDayAbbreviationAndTime() {
        val iso = Instant.now().minusSeconds(3L * 24 * 3600).toString()
        val result = messageTimeLabel(iso)
        assertFalse("Should not be empty", result.isEmpty())
        assertFalse("Should not be 'Yesterday'", result.startsWith("Yesterday"))
        assertTrue(
            "Should contain AM or PM for the time part",
            result.contains("AM") || result.contains("PM"),
        )
    }

    @Test
    fun messageTimeLabel_10DaysAgo_containsMonthDayAndTime() {
        val iso = Instant.now().minusSeconds(10L * 24 * 3600).toString()
        val result = messageTimeLabel(iso)
        assertFalse("Should not be empty", result.isEmpty())
        assertTrue(
            "Should contain AM or PM for the time part",
            result.contains("AM") || result.contains("PM"),
        )
    }

    @Test
    fun messageTimeLabel_invalidString_returnsEmpty() {
        assertEquals("", messageTimeLabel("bad-input"))
    }

    // ── gapExceedsTenMinutes ──────────────────────────────────────────────────

    @Test
    fun gapExceedsTenMinutes_9MinutesApart_returnsFalse() {
        val earlier = "2024-01-15T10:00:00Z"
        val later = "2024-01-15T10:09:00Z"
        assertFalse(gapExceedsTenMinutes(earlier, later))
    }

    @Test
    fun gapExceedsTenMinutes_exactly10Minutes_returnsFalse() {
        // The check is (later - earlier) > 10 * 60_000L, so exactly 10 min → false
        val earlier = "2024-01-15T10:00:00Z"
        val later = "2024-01-15T10:10:00Z"
        assertFalse(gapExceedsTenMinutes(earlier, later))
    }

    @Test
    fun gapExceedsTenMinutes_11MinutesApart_returnsTrue() {
        val earlier = "2024-01-15T10:00:00Z"
        val later = "2024-01-15T10:11:00Z"
        assertTrue(gapExceedsTenMinutes(earlier, later))
    }

    @Test
    fun gapExceedsTenMinutes_sameTimestamp_returnsFalse() {
        val ts = "2024-01-15T10:00:00Z"
        assertFalse(gapExceedsTenMinutes(ts, ts))
    }

    @Test
    fun gapExceedsTenMinutes_invalidEarlier_returnsFalse() {
        assertFalse(gapExceedsTenMinutes("bad", "2024-01-15T10:00:00Z"))
    }

    @Test
    fun gapExceedsTenMinutes_invalidLater_returnsFalse() {
        assertFalse(gapExceedsTenMinutes("2024-01-15T10:00:00Z", "bad"))
    }

    @Test
    fun gapExceedsTenMinutes_1HourApart_returnsTrue() {
        val earlier = "2024-01-15T10:00:00Z"
        val later = "2024-01-15T11:00:00Z"
        assertTrue(gapExceedsTenMinutes(earlier, later))
    }
}
