package com.sandnes.familyapp.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant

/**
 * Unit tests for the pure, testable helpers behind [FamilyMapViewModel] / FamilyMapScreen:
 * last-seen formatting, the 5-minute live/stale threshold, place-name formatting, and instant
 * parsing. These are deterministic functions with an injectable `nowMs`, so no Android / Supabase
 * scaffolding is required.
 */
@RunWith(JUnit4::class)
class FamilyMapViewModelTest {
    private val base: Long = Instant.parse("2026-07-10T12:00:00Z").toEpochMilli()
    private val baseIso = "2026-07-10T12:00:00Z"

    private val oneMinute = 60_000L
    private val oneHour = 60 * oneMinute
    private val oneDay = 24 * oneHour

    // ── parseInstantMs ──────────────────────────────────────────────

    @Test
    fun `parseInstantMs parses a valid ISO-8601 timestamp`() {
        assertEquals(base, parseInstantMs(baseIso))
    }

    @Test
    fun `parseInstantMs returns null for null`() {
        assertNull(parseInstantMs(null))
    }

    @Test
    fun `parseInstantMs returns null for garbage`() {
        assertNull(parseInstantMs("not-a-date"))
    }

    // ── isLocationLive (5-minute threshold) ─────────────────────────

    @Test
    fun `isLocationLive is true when updated just now`() {
        assertTrue(isLocationLive(baseIso, nowMs = base))
    }

    @Test
    fun `isLocationLive is true just under five minutes`() {
        assertTrue(isLocationLive(baseIso, nowMs = base + 5 * oneMinute - 1))
    }

    @Test
    fun `isLocationLive is false at exactly five minutes`() {
        assertFalse(isLocationLive(baseIso, nowMs = base + 5 * oneMinute))
    }

    @Test
    fun `isLocationLive is false when older than five minutes`() {
        assertFalse(isLocationLive(baseIso, nowMs = base + 10 * oneMinute))
    }

    @Test
    fun `isLocationLive is false for null or unparseable timestamps`() {
        assertFalse(isLocationLive(null, nowMs = base))
        assertFalse(isLocationLive("garbage", nowMs = base))
    }

    // ── formatLastSeen ──────────────────────────────────────────────

    @Test
    fun `formatLastSeen returns Unknown for null`() {
        assertEquals("Unknown", formatLastSeen(null, nowMs = base))
    }

    @Test
    fun `formatLastSeen returns Location shared for unparseable input`() {
        assertEquals("Location shared", formatLastSeen("garbage", nowMs = base))
    }

    @Test
    fun `formatLastSeen returns Just now for under a minute`() {
        assertEquals("Just now", formatLastSeen(baseIso, nowMs = base + 30_000L))
    }

    @Test
    fun `formatLastSeen returns Just now for clock-skew futures`() {
        assertEquals("Just now", formatLastSeen(baseIso, nowMs = base - 30_000L))
    }

    @Test
    fun `formatLastSeen returns minutes for under an hour`() {
        assertEquals("5 min ago", formatLastSeen(baseIso, nowMs = base + 5 * oneMinute))
    }

    @Test
    fun `formatLastSeen returns hours for under a day`() {
        assertEquals("3 hours ago", formatLastSeen(baseIso, nowMs = base + 3 * oneHour))
    }

    @Test
    fun `formatLastSeen returns a date for older than a day`() {
        val result = formatLastSeen(baseIso, nowMs = base + 2 * oneDay)
        assertFalse("should not be a relative label", result.endsWith("ago"))
        assertTrue("should look like a formatted date", result.contains("2026"))
    }

    // ── formatPlaceDetail ───────────────────────────────────────────

    @Test
    fun `formatPlaceDetail joins place and last-seen`() {
        assertEquals("Oslo · 5 min ago", formatPlaceDetail("Oslo", "5 min ago"))
    }

    @Test
    fun `formatPlaceDetail falls back to last-seen when place is null`() {
        assertEquals("5 min ago", formatPlaceDetail(null, "5 min ago"))
    }

    @Test
    fun `formatPlaceDetail falls back to last-seen when place is blank`() {
        assertEquals("5 min ago", formatPlaceDetail("   ", "5 min ago"))
    }
}
