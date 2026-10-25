package com.example.bible.data

import java.time.LocalDate

/** Возрастной режим раздела «Детям». */
enum class KidsAgeMode {
    SMALL,
    BIG,
    ;

    val title: String
        get() = when (this) {
            SMALL -> "Малыши 3–5"
            BIG -> "Школьники 6–10"
        }

    val emoji: String
        get() = when (this) {
            SMALL -> "🐣"
            BIG -> "🎒"
        }
}

/** Возраст ребёнка подсказывает режим: до 5 лет включительно — малыши, дальше — школьники. */
fun suggestedKidsAgeMode(age: Int): KidsAgeMode =
    if (age <= 5) KidsAgeMode.SMALL else KidsAgeMode.BIG

/** Западная Пасха (григорианский календарь), алгоритм Гаусса / Meeus. */
fun westernEaster(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}

/**
 * Православная Пасха. Сначала дата по юлианскому календарю, затем сдвиг
 * на григорианский: +13 дней в 1900–2099 годах.
 */
fun orthodoxEaster(year: Int): LocalDate {
    val a = year % 4
    val b = year % 7
    val c = year % 19
    val d = (19 * c + 15) % 30
    val e = (2 * a + 4 * b - d + 34) % 7
    val julianMonth = (d + e + 114) / 31
    val julianDay = ((d + e + 114) % 31) + 1
    val shift = if (year in 1900..2099) 13L else 13L
    return LocalDate.of(year, julianMonth, julianDay).plusDays(shift)
}

fun nextEaster(today: LocalDate, orthodox: Boolean): LocalDate {
    fun of(year: Int) = if (orthodox) orthodoxEaster(year) else westernEaster(year)
    val thisYear = of(today.year)
    return if (!thisYear.isBefore(today)) thisYear else of(today.year + 1)
}

/** Крупная плитка Пасхи: за 14 дней до праздника и в сам день. */
fun easterSeason(today: LocalDate, easter: LocalDate): Boolean {
    val start = easter.minusDays(14)
    return !today.isBefore(start) && !today.isAfter(easter)
}

fun verseTokens(text: String): List<String> =
    text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Склеивает слова в [parts] крупных плашек (для малышей и первых дней). */
fun verseChunks(tokens: List<String>, parts: Int): List<String> {
    if (tokens.isEmpty()) return emptyList()
    val n = parts.coerceIn(1, tokens.size)
    val size = (tokens.size + n - 1) / n
    return tokens.chunked(size).map { it.joinToString(" ") }
}

/** ★ прошёл, ★★ почти без ошибок, ★★★ без ошибок и подсказок. */
fun kidsStarsForRun(errors: Int, hints: Int): Int = when {
    errors <= 0 && hints <= 0 -> 3
    errors <= 1 && hints <= 0 -> 2
    else -> 1
}
