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
