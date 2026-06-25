package com.example.mainactivity.ui.chat

/** Short relative time label for conversation list preview (e.g. "2m ago", "Yesterday", "Mon"). */
internal fun relativeTime(isoString: String): String =
    try {
        val instant = java.time.Instant.parse(isoString)
        val nowMs = System.currentTimeMillis()
        val diffMs = nowMs - instant.toEpochMilli()
        val diffMin = diffMs / 60_000
        val diffH = diffMs / 3_600_000
        val diffD = diffMs / 86_400_000
        when {
            diffMin < 1 -> "now"
            diffMin < 60 -> "${diffMin}m ago"
            diffH < 24 -> "${diffH}h ago"
            diffD == 1L -> "Yesterday"
            diffD < 7 -> {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = instant.toEpochMilli()
                cal.getDisplayName(
                    java.util.Calendar.DAY_OF_WEEK,
                    java.util.Calendar.SHORT,
                    java.util.Locale.getDefault(),
                ) ?: ""
            }
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = instant.toEpochMilli()
                val month =
                    cal.getDisplayName(
                        java.util.Calendar.MONTH,
                        java.util.Calendar.SHORT,
                        java.util.Locale.getDefault(),
                    ) ?: ""
                "$month ${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
            }
        }
    } catch (e: Exception) {
        ""
    }

/** Compact time label for message timestamps (e.g. "2:30 PM", "Yesterday 2:30 PM", "Mon 2:30 PM"). */
internal fun messageTimeLabel(isoString: String): String =
    try {
        val instant = java.time.Instant.parse(isoString)
        val nowMs = System.currentTimeMillis()
        val diffMs = nowMs - instant.toEpochMilli()
        val diffD = diffMs / 86_400_000
        val odt = java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val timePart =
            odt.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("h:mm a"),
            )
        when {
            diffD == 0L -> timePart
            diffD == 1L -> "Yesterday $timePart"
            diffD < 7 -> {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = instant.toEpochMilli()
                val dow =
                    cal.getDisplayName(
                        java.util.Calendar.DAY_OF_WEEK,
                        java.util.Calendar.SHORT,
                        java.util.Locale.getDefault(),
                    ) ?: ""
                "$dow $timePart"
            }
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = instant.toEpochMilli()
                val month =
                    cal.getDisplayName(
                        java.util.Calendar.MONTH,
                        java.util.Calendar.SHORT,
                        java.util.Locale.getDefault(),
                    ) ?: ""
                "$month ${cal.get(java.util.Calendar.DAY_OF_MONTH)} $timePart"
            }
        }
    } catch (e: Exception) {
        ""
    }

/** Returns true if the gap between two ISO timestamps exceeds 10 minutes. */
internal fun gapExceedsTenMinutes(
    earlierIso: String,
    laterIso: String,
): Boolean =
    try {
        val earlier =
            java.time.Instant
                .parse(earlierIso)
                .toEpochMilli()
        val later =
            java.time.Instant
                .parse(laterIso)
                .toEpochMilli()
        (later - earlier) > 10 * 60_000L
    } catch (e: Exception) {
        false
    }
