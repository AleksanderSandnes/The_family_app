package com.example.mainactivity.ui.home

import java.util.Calendar

/** Returns "Good morning", "Good afternoon", or "Good evening" based on the hour. */
internal fun timeBasedGreeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
