package com.example.bible.data

import java.time.LocalDate

data class PassageRef(
    val bookId: String,
    val chapter: Int,
    val displayName: String,
)

data class DayReading(
    val dayOfYear: Int,
    val passages: List<PassageRef>,
)

object ReadingPlan {

    private val plan: List<DayReading> by lazy { buildPlan() }

    fun forDate(date: LocalDate = LocalDate.now()): DayReading {
        val day = date.dayOfYear.coerceIn(1, plan.size)
        return plan[day - 1]
    }

    fun progressPercent(date: LocalDate = LocalDate.now()): Int {
        val day = date.dayOfYear
        return (day * 100 / 365).coerceIn(0, 100)
    }

    private fun buildPlan(): List<DayReading> {
        val otBookIds = BibleCanon.allBooks
            .filter { BibleCanon.isOldTestament(it.id) }
            .map { it }
        val ntBookIds = BibleCanon.allBooks
            .filter { BibleCanon.isNewTestament(it.id) }
            .map { it }

        val otChapters = mutableListOf<PassageRef>()
        for (entry in otBookIds) {
            for (ch in 1..entry.chapters) {
                otChapters.add(PassageRef(entry.id, ch, "${entry.abbrRu} $ch"))
            }
        }
        val ntChapters = mutableListOf<PassageRef>()
        for (entry in ntBookIds) {
            for (ch in 1..entry.chapters) {
                ntChapters.add(PassageRef(entry.id, ch, "${entry.abbrRu} $ch"))
            }
        }

        val days = mutableListOf<DayReading>()
        val otPerDay = (otChapters.size + 364) / 365
        val ntPerDay = (ntChapters.size + 364) / 365

        var otIdx = 0
        var ntIdx = 0
        for (d in 1..365) {
            val passages = mutableListOf<PassageRef>()
            repeat(otPerDay) {
                if (otIdx < otChapters.size) passages.add(otChapters[otIdx++])
            }
            repeat(ntPerDay) {
                if (ntIdx < ntChapters.size) passages.add(ntChapters[ntIdx++])
            }
            if (passages.isEmpty() && otIdx < otChapters.size) {
                passages.add(otChapters[otIdx++])
            }
            days.add(DayReading(d, passages))
        }
        while (otIdx < otChapters.size) {
            val last = days.last()
            days[days.lastIndex] = last.copy(passages = last.passages + otChapters[otIdx++])
        }
        while (ntIdx < ntChapters.size) {
            val last = days.last()
            days[days.lastIndex] = last.copy(passages = last.passages + ntChapters[ntIdx++])
        }
        return days
    }
}
