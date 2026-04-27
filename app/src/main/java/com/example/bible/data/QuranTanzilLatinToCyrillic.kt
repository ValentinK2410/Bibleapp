package com.example.bible.data

import java.util.Locale

/**
 * Приближённая кириллическая транслитерация для строк в формате quran-json / Tanzil (латиница).
 * Цель — удобное чтение по-русски, без претензии на единый академический стандарт.
 */
object QuranTanzilLatinToCyrillic {

    private const val AYN = '\u0007' // временный маркер для ʿайна (AA)

    fun convert(latin: String): String {
        if (latin.isBlank()) return latin.trim()
        var x = latin.trim()
        // Целые слова и устойчивые формы (длиннее — раньше)
        for ((rx, rep) in WHOLE_WORD_REPLACEMENTS) {
            x = rx.replace(x, rep)
        }
        for ((rx, rep) in MORPH_WORD_REPLACEMENTS) {
            x = rx.replace(x, rep)
        }
        x = x.replace("AA", AYN.toString())
        // Солнце-поглощение: al + удвоенная согласная → а + согласная (удвоенная)
        x = SUN_LAM_REGEX.replace(x) { m ->
            val d = m.groupValues[1].lowercase()
            val cyr = SUN_DOUBLED[d] ?: (d.firstOrNull()?.let { LATIN_CONSONANT[it] } ?: d)
            "а$cyr$cyr"
        }
        // Дифтонги / долгие гласные (после AA → AYN)
        for ((pat, cyr) in VOWEL_DIGRAPHS) {
            x = pat.replace(x, cyr)
        }
        for ((pat, cyr) in CONSONANT_DIGRAPHS) {
            x = pat.replace(x, cyr)
        }
        x = x.replace(AYN.toString(), "'")
        // Оставшиеся латинские буквы → кириллица (с учётом заглавных как эмфатических)
        val out = StringBuilder(x.length + 16)
        for (ch in x) {
            when {
                ch.isUpperCase() && ch.isLetter() -> {
                    val low = ch.lowercaseChar()
                    EMPHATIC_UPPER[low]?.let { out.append(titleRu(it)) }
                        ?: LATIN_CONSONANT[low]?.let { out.append(titleRu(it)) }
                        ?: LATIN_VOWEL[low]?.let { out.append(titleRu(it)) }
                        ?: out.append(ch)
                }
                ch.isLowerCase() -> {
                    LATIN_CONSONANT[ch]?.let { out.append(it) }
                        ?: LATIN_VOWEL[ch]?.let { out.append(it) }
                        ?: out.append(ch)
                }
                else -> out.append(ch)
            }
        }
        return out.toString()
            .replace(Regex(" +"), " ")
            .trim()
    }

    private fun titleRu(s: String): String =
        s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("ru")) else it.toString() }

    private val WHOLE_WORD_REPLACEMENTS: List<Pair<Regex, String>> = listOf(
        Regex("""\bAliflammeem\b""", RegexOption.IGNORE_CASE) to "Алиф-лям-мим",
        Regex("""\bBismi\b""", RegexOption.IGNORE_CASE) to "Бисми",
        Regex("""\bAllahu\b""", RegexOption.IGNORE_CASE) to "Аллаху",
        Regex("""\bAllahi\b""", RegexOption.IGNORE_CASE) to "Аллахи",
        Regex("""\bAllah\b""", RegexOption.IGNORE_CASE) to "Аллах",
        Regex("""\bLillahi\b""", RegexOption.IGNORE_CASE) to "лилляхи",
        Regex("""\bbiAllahi\b""", RegexOption.IGNORE_CASE) to "би-Аллахи",
        Regex("""\bbialyawmi\b""", RegexOption.IGNORE_CASE) to "би-аль-йауми",
        Regex("""\balakhiri\b""", RegexOption.IGNORE_CASE) to "аль-ахири",
        Regex("""\balhaqqi\b""", RegexOption.IGNORE_CASE) to "аль-Хакки",
        Regex("""\balkitaba\b""", RegexOption.IGNORE_CASE) to "аль-Китаба",
        Regex("""\bMuhammad\b""", RegexOption.IGNORE_CASE) to "Мухаммад",
        Regex("""\bMuhammadun\b""", RegexOption.IGNORE_CASE) to "Мухаммадун",
        Regex("""\bRasool\b""", RegexOption.IGNORE_CASE) to "Расуль",
        Regex("""\bQuran\b""", RegexOption.IGNORE_CASE) to "Коран",
    )

    private val MORPH_WORD_REPLACEMENTS: List<Pair<Regex, String>> = listOf(
        Regex("""\ballatheena\b""", RegexOption.IGNORE_CASE) to "аллязина",
        Regex("""\ballatheenu\b""", RegexOption.IGNORE_CASE) to "аллязину",
        Regex("""\ballatheeni\b""", RegexOption.IGNORE_CASE) to "аллязини",
        Regex("""\ballazeena\b""", RegexOption.IGNORE_CASE) to "алязина",
        Regex("""\ballazeenu\b""", RegexOption.IGNORE_CASE) to "алязину",
        Regex("""\ballatheen\b""", RegexOption.IGNORE_CASE) to "аллязин",
        Regex("""\ballazeen\b""", RegexOption.IGNORE_CASE) to "алязин",
    )

    private val SUN_LAM_REGEX =
        Regex("""\bAl(rr|nn|ss|tt|dd|ll|zz|sh|th|dh|bb|mm|ff|kk|gg|jj|vv|ww|yy|qq|pp|cc)\b""", RegexOption.IGNORE_CASE)
    private val SUN_DOUBLED = mapOf(
        "rr" to "р", "nn" to "н", "ss" to "с", "tt" to "т", "dd" to "д", "ll" to "л",
        "zz" to "з", "sh" to "ш", "th" to "с", "dh" to "з", "bb" to "б", "mm" to "м",
        "ff" to "ф", "kk" to "к", "gg" to "г", "jj" to "дж", "vv" to "в", "ww" to "в",
        "yy" to "й", "qq" to "к", "pp" to "п", "cc" to "к",
    )

    private val VOWEL_DIGRAPHS: List<Pair<Regex, String>> = listOf(
        Regex("oo", RegexOption.IGNORE_CASE) to "у",
        Regex("ee", RegexOption.IGNORE_CASE) to "и",
        Regex("uu", RegexOption.IGNORE_CASE) to "у",
        Regex("ii", RegexOption.IGNORE_CASE) to "и",
        Regex("aa", RegexOption.IGNORE_CASE) to "а",
        Regex("ai", RegexOption.IGNORE_CASE) to "ай",
        Regex("ay", RegexOption.IGNORE_CASE) to "ай",
        Regex("ou", RegexOption.IGNORE_CASE) to "у",
        Regex("ei", RegexOption.IGNORE_CASE) to "ей",
    )

    private val CONSONANT_DIGRAPHS: List<Pair<Regex, String>> = listOf(
        Regex("sh", RegexOption.IGNORE_CASE) to "ш",
        Regex("gh", RegexOption.IGNORE_CASE) to "г",
        Regex("kh", RegexOption.IGNORE_CASE) to "х",
        Regex("th", RegexOption.IGNORE_CASE) to "с",
        Regex("dh", RegexOption.IGNORE_CASE) to "з",
        Regex("ch", RegexOption.IGNORE_CASE) to "ч",
        Regex("ph", RegexOption.IGNORE_CASE) to "ф",
        Regex("qu", RegexOption.IGNORE_CASE) to "ку",
    )

    private val EMPHATIC_UPPER = mapOf(
        's' to "с",
        'd' to "д",
        't' to "т",
        'z' to "з",
        'h' to "х",
    )

    private val LATIN_CONSONANT = mapOf(
        'b' to "б", 't' to "т", 'j' to "дж", 'd' to "д", 'r' to "р", 'z' to "з",
        's' to "с", 'f' to "ф", 'q' to "к", 'k' to "к", 'l' to "л", 'm' to "м",
        'n' to "н", 'h' to "х", 'w' to "в", 'y' to "й", 'g' to "г", 'p' to "п",
        'v' to "в", 'c' to "к", 'x' to "кс",
    )

    private val LATIN_VOWEL = mapOf(
        'a' to "а", 'e' to "е", 'i' to "и", 'o' to "о", 'u' to "у",
    )
}
