package com.sandnes.familyapp.ui.chat

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val PRESENCE_ACTIVE_NOW_MINUTES = 2
private const val MESSAGE_GROUP_GAP_MILLIS = 10 * MILLIS_PER_MINUTE

private fun dayOfWeekShort(epochMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
}

private fun monthDayShort(epochMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
    return "$month ${cal.get(Calendar.DAY_OF_MONTH)}"
}

/**
 * Robust ISO-8601 → [Instant]. Supabase timestamptz values carry a "+00:00" offset (and no
 * trailing Z), which [Instant.parse] rejects on Android's desugared java.time — every chat
 * timestamp then silently rendered as empty. Falls back to [OffsetDateTime] parsing.
 */
internal fun parseInstant(iso: String): Instant? =
    runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching {
            java.time.LocalDateTime
                .parse(iso)
                .atZone(ZoneId.of("UTC"))
                .toInstant()
        }.getOrNull()

/** Short relative time label for conversation list preview (e.g. "2m ago", "Yesterday", "Mon"). */
internal fun relativeTime(isoString: String): String =
    runCatching {
        val instant = parseInstant(isoString) ?: return ""
        val diffMs = System.currentTimeMillis() - instant.toEpochMilli()
        val diffMin = diffMs / MILLIS_PER_MINUTE
        val diffH = diffMs / MILLIS_PER_HOUR
        val diffD = diffMs / MILLIS_PER_DAY
        when {
            diffMin < 1 -> "now"
            diffMin < MINUTES_PER_HOUR -> "${diffMin}m ago"
            diffH < HOURS_PER_DAY -> "${diffH}h ago"
            diffD == 1L -> "Yesterday"
            diffD < DAYS_PER_WEEK -> dayOfWeekShort(instant.toEpochMilli())
            else -> monthDayShort(instant.toEpochMilli())
        }
    }.getOrDefault("")

/** Compact time label for message timestamps (e.g. "2:30 PM", "Yesterday 2:30 PM", "Mon 2:30 PM"). */
internal fun messageTimeLabel(isoString: String): String =
    runCatching {
        val instant = parseInstant(isoString) ?: return ""
        val diffD = (System.currentTimeMillis() - instant.toEpochMilli()) / MILLIS_PER_DAY
        val odt = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
        val timePart = odt.format(DateTimeFormatter.ofPattern("h:mm a"))
        when {
            diffD == 0L -> timePart
            diffD == 1L -> "Yesterday $timePart"
            diffD < DAYS_PER_WEEK -> "${dayOfWeekShort(instant.toEpochMilli())} $timePart"
            else -> "${monthDayShort(instant.toEpochMilli())} $timePart"
        }
    }.getOrDefault("")

/** Chat-header presence: "Active now" within 2 minutes, else "Active {relative}", or null. */
internal fun presenceLabel(lastActiveIso: String?): String? {
    if (lastActiveIso == null) return null
    return runCatching {
        val instant = parseInstant(lastActiveIso) ?: return null
        val diffMin = (System.currentTimeMillis() - instant.toEpochMilli()) / MILLIS_PER_MINUTE
        if (diffMin < PRESENCE_ACTIVE_NOW_MINUTES) "Active now" else "Active ${relativeTime(lastActiveIso)}"
    }.getOrNull()
}

/** True if another participant's last-read time [otherLastRead] is at or after [sentAt],
 *  i.e. they've seen the message. */
internal fun messageSeen(
    otherLastRead: String?,
    sentAt: String,
): Boolean {
    if (otherLastRead == null) return false
    return runCatching {
        val a = parseInstant(otherLastRead) ?: return false
        val b = parseInstant(sentAt) ?: return false
        !a.isBefore(b)
    }.getOrDefault(false)
}

/** Returns true if the gap between two ISO timestamps exceeds 10 minutes. */
internal fun gapExceedsTenMinutes(
    earlierIso: String,
    laterIso: String,
): Boolean =
    runCatching {
        val earlier = parseInstant(earlierIso)?.toEpochMilli() ?: return false
        val later = parseInstant(laterIso)?.toEpochMilli() ?: return false
        (later - earlier) > MESSAGE_GROUP_GAP_MILLIS
    }.getOrDefault(false)

/**
 * Full exact timestamp revealed when a message bubble is tapped
 * (e.g. "Jul 7, 2026, 2:30 PM" for en, "7. juli 2026, 14:30" for nb). Mirrors iOS
 * `exactMessageTimestamp` (medium date + short time, locale-aware).
 */
internal fun exactMessageTimestamp(isoString: String): String {
    val instant = parseInstant(isoString) ?: return ""
    val formatter =
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
            Locale.getDefault(),
        )
    return formatter.format(java.util.Date(instant.toEpochMilli()))
}
