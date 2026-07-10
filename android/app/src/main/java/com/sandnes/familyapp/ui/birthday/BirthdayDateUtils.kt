package com.sandnes.familyapp.ui.birthday

import java.time.LocalDate

internal fun nextBirthdayDate(
    isoDate: String,
    today: LocalDate = LocalDate.now(),
): LocalDate? =
    runCatching {
        val bd = LocalDate.parse(isoDate)
        val thisYear = bd.withYear(today.year)
        if (thisYear < today) bd.withYear(today.year + 1) else thisYear
    }.getOrNull()

internal fun turnsAge(
    isoDate: String,
    today: LocalDate = LocalDate.now(),
): Int? =
    runCatching {
        val bd = LocalDate.parse(isoDate)
        val next = nextBirthdayDate(isoDate, today) ?: return@runCatching null
        next.year - bd.year
    }.getOrNull()

/** Urgency buckets for the countdown pill. Mirrors iOS: today, within a week, later. */
internal enum class BirthdayUrgency { TODAY, SOON, LATER }

/** Days threshold below which a birthday is "soon" (amber-tinted pill). */
internal const val BIRTHDAY_SOON_DAYS = 7

/** Classifies a days-until-next-birthday count into an urgency bucket. */
internal fun birthdayUrgency(daysUntil: Int): BirthdayUrgency =
    when {
        daysUntil <= 0 -> BirthdayUrgency.TODAY
        daysUntil <= BIRTHDAY_SOON_DAYS -> BirthdayUrgency.SOON
        else -> BirthdayUrgency.LATER
    }
