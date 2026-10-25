package com.example.bible.data

import android.content.Context
import java.time.LocalDate
import java.time.temporal.IsoFields

data class KidsMemoryVerse(
    val id: String,
    val reference: String,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    /** Фрагмент для игры. Должен встречаться в синодальном стихе. */
    val gameText: String,
    /** Полный стих из assets/bible/SYN — запасной, если база ещё не открыта. */
    val fullText: String,
    val age: String,
)

object KidsMemoryVerses {
    val all: List<KidsMemoryVerse> = listOf(
        verse("gen_1_1", "Быт 1:1", "genesis", 1, 1, "small", "В начале сотворил Бог небо и землю."),
        verse(
            "ps_22_1", "Пс 22:1", "psalms", 22, 1, "small",
            "Господь - Пастырь мой; я ни в чем не буду нуждаться:",
        ),
        verse(
            "1jn_4_8", "1 Ин 4:8", "1_john", 4, 8, "small",
            "Кто не любит, тот не познал Бога, потому что Бог есть любовь.",
            game = "Бог есть любовь.",
        ),
        verse(
            "mt_22_39", "Мф 22:39", "matthew", 22, 39, "small",
            "вторая же подобная ей: возлюби ближнего твоего, как самого себя;",
            game = "возлюби ближнего твоего, как самого себя",
        ),
        verse(
            "mt_19_14", "Мф 19:14", "matthew", 19, 14, "big",
            "Но Иисус сказал: пустите детей и не препятствуйте им приходить ко Мне, ибо таковых есть Царство Небесное.",
        ),
        verse("php_4_13", "Флп 4:13", "philippians", 4, 13, "big", "Все могу в укрепляющем меня Иисусе Христе."),
        verse(
            "ps_118_105", "Пс 118:105", "psalms", 118, 105, "big",
            "Слово Твое - светильник ноге моей и свет стезе моей.",
        ),
        verse(
            "jn_14_6", "Ин 14:6", "john", 14, 6, "big",
            "Иисус сказал ему: Я есмь путь и истина и жизнь; никто не приходит к Отцу, как только через Меня.",
            game = "Я есмь путь и истина и жизнь",
        ),
        verse(
            "eph_4_32", "Еф 4:32", "ephesians", 4, 32, "big",
            "но будьте друг ко другу добры, сострадательны, прощайте друг друга, как и Бог во Христе простил вас.",
            game = "будьте друг ко другу добры, сострадательны, прощайте друг друга, как и Бог во Христе простил вас.",
        ),
        verse(
            "prv_3_5", "Притч 3:5", "proverbs", 3, 5, "big",
            "Надейся на Господа всем сердцем твоим, и не полагайся на разум твой.",
        ),
        verse(
            "isa_41_10", "Ис 41:10", "isaiah", 41, 10, "big",
            "не бойся, ибо Я с тобою; не смущайся, ибо Я Бог твой; Я укреплю тебя, и помогу тебе, и поддержу тебя десницею правды Моей.",
            game = "не бойся, ибо Я с тобою; не смущайся, ибо Я Бог твой",
        ),
        verse(
            "jn_3_16", "Ин 3:16", "john", 3, 16, "big",
            "Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного, дабы всякий верующий в Него, не погиб, но имел жизнь вечную.",
        ),
    )

    fun forAge(mode: KidsAgeMode): List<KidsMemoryVerse> = when (mode) {
        KidsAgeMode.SMALL -> all.filter { it.age == "small" }
        KidsAgeMode.BIG -> all
    }

    fun ofWeek(today: LocalDate, mode: KidsAgeMode): KidsMemoryVerse {
        val pool = forAge(mode)
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val index = Math.floorMod(today.year * 53 + week, pool.size)
        return pool[index]
    }

    fun byId(id: String): KidsMemoryVerse? = all.firstOrNull { it.id == id }

    /** День задания внутри недели: 1 послушать … 7 наизусть. */
    fun taskDay(today: LocalDate): Int = today.dayOfWeek.value

    fun taskTitle(day: Int, mode: KidsAgeMode): String = when (day) {
        1 -> "Послушай и прочитай вместе"
        2, 3 -> if (mode == KidsAgeMode.SMALL) "Собери из больших частей" else "Собери из крупных частей"
        4, 5 -> if (mode == KidsAgeMode.SMALL) "Собери стих" else "Собери все слова"
        6 -> "Заполни пропуски"
        else -> "Расскажи наизусть"
    }
}

private fun verse(
    id: String,
    reference: String,
    bookId: String,
    chapter: Int,
    verse: Int,
    age: String,
    full: String,
    game: String = full,
) = KidsMemoryVerse(id, reference, bookId, chapter, verse, game, full, age)

/** Стих из базы приложения. null, если перевод ещё не импортирован. */
fun synodalVerseOrNull(context: Context, bookId: String, chapter: Int, verse: Int): String? =
    runCatching {
        BibleRepository(context.applicationContext)
            .loadChapter(TranslationId.SYNODAL, bookId, chapter)
            ?.verses
            ?.firstOrNull { it.number == verse }
            ?.text
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

fun verseContainsFragment(full: String, fragment: String): Boolean {
    val norm = full.lowercase().replace('ё', 'е')
    val piece = fragment.lowercase().replace('ё', 'е').trim().trimEnd(':', ';', '.', ',', ' ')
    return piece.isNotEmpty() && norm.contains(piece)
}

/** Фрагмент игры должен быть куском полного синодального стиха. */
fun KidsMemoryVerse.gameIsSubstringOfFull(): Boolean {
    val full = fullText.lowercase().replace('ё', 'е')
    val game = gameText.lowercase().replace('ё', 'е')
    return full.contains(game)
}
