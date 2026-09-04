package com.example.bible.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RuDateFull: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

private val RuMonthDay: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

/** Календарный день рождения в заданном году (29 февраля безопасно переносим на 28.02 в невисокосные годы). */
internal fun calendarBirthdayInYear(monthValue: Int, dayOfMonth: Int, calendarYear: Int): LocalDate =
    runCatching { LocalDate.of(calendarYear, monthValue, dayOfMonth) }
        .getOrElse { LocalDate.of(calendarYear, 2, 28) }

/** Резервное чтение даты текстом вида yyyy-MM-dd. */
internal fun birthdayEpochDayFromLegacyJson(raw: String): Long? {
    val s = raw.trim()
    if (s.isBlank()) return null
    val today = LocalDate.now()
    return runCatching { LocalDate.parse(s) }.getOrNull()
        ?.takeIf { !it.isAfter(today) && it.year >= 1900 }
        ?.toEpochDay()
}

fun UserContact.birthLocalDate(): LocalDate? =
    birthEpochDay?.takeIf { it in -40000L..200_000L }?.let(LocalDate::ofEpochDay)

fun UserContact.nextBirthdayCalendarDate(from: LocalDate): LocalDate? {
    val b = birthLocalDate() ?: return null
    var next = calendarBirthdayInYear(b.monthValue, b.dayOfMonth, from.year)
    if (next.isBefore(from)) {
        next = calendarBirthdayInYear(b.monthValue, b.dayOfMonth, from.year + 1)
    }
    return next
}

fun UserContact.daysUntilNextBirthday(from: LocalDate): Int? {
    val next = nextBirthdayCalendarDate(from) ?: return null
    return ChronoUnit.DAYS.between(from, next).toInt().coerceAtLeast(0)
}

fun UserContact.completedAgeYears(from: LocalDate): Int? {
    val b = birthLocalDate() ?: return null
    return Period.between(b, from).years.coerceAtLeast(0)
}

fun LocalDate.formatBirthDateRuFull(): String = format(RuDateFull)

fun LocalDate.formatMonthDayRu(): String = format(RuMonthDay)

fun formatYearsRussian(years: Int): String =
    when {
        years % 100 in 11..19 -> "$years лет"
        years % 10 == 1 -> "$years год"
        years % 10 in 2..4 -> "$years года"
        else -> "$years лет"
    }

/** Пустая строка → без даты; иначе строго ISO yyyy-MM-dd. Null при ошибке. */
internal fun validateBirthEpochDayInput(rawTrimmed: String): Long? {
    if (rawTrimmed.isBlank()) return null
    val today = LocalDate.now()
    val d = runCatching { LocalDate.parse(rawTrimmed) }.getOrNull()
        ?: return null
    if (d.year < 1900 || d.isAfter(today)) return null
    return d.toEpochDay()
}
