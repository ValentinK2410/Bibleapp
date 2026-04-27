package com.example.bible.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ReadingPlanProgress {

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    /** Дней с начала года до [today], где чтение ещё не отмечено выполненным. */
    fun countMissedDays(completed: Set<String>, today: LocalDate): Int {
        var d = LocalDate.ofYearDay(today.year, 1)
        var n = 0
        while (!d.isAfter(today)) {
            val key = d.format(iso)
            val day = ReadingPlan.forDate(d)
            if (day.passages.isNotEmpty() && key !in completed) n++
            d = d.plusDays(1)
        }
        return n
    }

    fun firstMissedDate(completed: Set<String>, today: LocalDate): LocalDate? {
        var d = LocalDate.ofYearDay(today.year, 1)
        while (!d.isAfter(today)) {
            val key = d.format(iso)
            val day = ReadingPlan.forDate(d)
            if (day.passages.isNotEmpty() && key !in completed) return d
            d = d.plusDays(1)
        }
        return null
    }
}
