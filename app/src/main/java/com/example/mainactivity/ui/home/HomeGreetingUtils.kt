package com.example.mainactivity.ui.home

import java.util.Calendar

private const val LAST_MORNING_HOUR = 11
private const val LAST_AFTERNOON_HOUR = 17

/** Returns "Good morning", "Good afternoon", or "Good evening" based on the hour. */
internal fun timeBasedGreeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..LAST_MORNING_HOUR -> "Good morning"
        in (LAST_MORNING_HOUR + 1)..LAST_AFTERNOON_HOUR -> "Good afternoon"
        else -> "Good evening"
    }
