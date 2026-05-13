package com.example.bible.data

import kotlin.random.Random

data class ReversedWordRound(
    val answerForward: String,
    /** Варианты ответов — нормализованные слова («труба» и т.д.). */
    val choices: List<String>,
)

/** Для заголовка: «труба» → «Абурт». */
fun prettifyReversedDisplay(forwardWord: String): String {
    val r = forwardWord.lowercase().reversed()
    return r.replaceFirstChar { it.uppercaseChar() }
}

fun nextReversedWordRound(random: Random): ReversedWordRound {
    val pool = azbukaReversedWordCandidates().filter { it.length >= 3 }.distinct()
    require(pool.size >= 4) { "Недостаточно слов для игры «наоборот»." }
    val answerForward = pool.random(random)
    val distractors = pool.filter { it != answerForward }.shuffled(random).take(3)
    val choices = (distractors + answerForward).shuffled(random)
    return ReversedWordRound(
        answerForward = answerForward,
        choices = choices,
    )
}
