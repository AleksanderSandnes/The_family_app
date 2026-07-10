package com.sandnes.familyapp.ui.home

import androidx.annotation.StringRes
import com.sandnes.familyapp.R
import java.util.Calendar

private const val LAST_MORNING_HOUR = 11
private const val LAST_AFTERNOON_HOUR = 17

/** Returns the string resource for a morning/afternoon/evening greeting based on the hour. */
@StringRes
internal fun timeBasedGreeting(): Int =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..LAST_MORNING_HOUR -> R.string.good_morning
        in (LAST_MORNING_HOUR + 1)..LAST_AFTERNOON_HOUR -> R.string.good_afternoon
        else -> R.string.good_evening
    }
