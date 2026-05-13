package com.example.bible.data

import kotlin.random.Random

/** Часть речи для детской игры «найди существительное / глагол / прилагательное». */
enum class PartsOfSpeechKind(val labelRu: String, val taskSpeakRu: String) {
    NOUN("Существительное", "Найди существительное."),
    VERB("Глагол", "Найди глагол."),
    ADJECTIVE("Прилагательное", "Найди прилагательное."),
}

data class PartsOfSpeechItem(
    val word: String,
    val kind: PartsOfSpeechKind,
)

data class PartsOfSpeechRound(
    val targetKind: PartsOfSpeechKind,
    val correct: PartsOfSpeechItem,
    val choices: List<PartsOfSpeechItem>,
)

/**
 * Простой набор слов для детей (несложная лексика). Категории заданы вручную для стабильной игры
 * без морфологического анализатора.
 */
private val PARTS_OF_SPEECH_WORDS: List<PartsOfSpeechItem> = buildList {
    // Существительные
    for (w in listOf(
        "дом", "мама", "папа", "кот", "собака", "солнце", "дерево", "книга", "рука", "нога",
        "река", "море", "лес", "школа", "друг", "сестра", "брат", "птица", "цветок", "яблоко",
        "мяч", "звезда", "облако", "снег", "дождь", "трава", "гриб", "труба",
    )) {
        add(PartsOfSpeechItem(w, PartsOfSpeechKind.NOUN))
    }
    // Глаголы (форма словарная, без «ходит» — только неопредёленная форма где уместно)
    for (w in listOf(
        "бежать", "идти", "плыть", "спать", "есть", "пить", "читать", "писать", "рисовать",
        "играть", "прыгать", "петь", "танцевать", "смеяться", "гулять", "учиться",
        "любить", "знать", "видеть", "слушать", "говорить", "помогать", "строить", "летать",
    )) {
        add(PartsOfSpeechItem(w, PartsOfSpeechKind.VERB))
    }
    // Прилагательные
    for (w in listOf(
        "большой", "маленький", "красный", "синий", "зелёный", "жёлтый", "добрый", "умный",
        "весёлый", "грустный", "быстрый", "медленный", "тёплый", "холодный", "мягкий", "твёрдый",
        "сладкий", "кислый", "яркий", "тёмный", "чистый", "новый", "хороший", "плохой",
    )) {
        add(PartsOfSpeechItem(w, PartsOfSpeechKind.ADJECTIVE))
    }
}

/** Слова для игр азбуки, где нужен общий словарь (например, «слово наоборот»). */
fun azbukaReversedWordCandidates(): List<String> =
    PARTS_OF_SPEECH_WORDS.map { it.word }.distinct()

fun nextPartsOfSpeechRound(random: Random): PartsOfSpeechRound {
    val targetKind = PartsOfSpeechKind.entries.random(random)
    val correctPool = PARTS_OF_SPEECH_WORDS.filter { it.kind == targetKind }
    val wrongPool = PARTS_OF_SPEECH_WORDS.filter { it.kind != targetKind }
    require(correctPool.isNotEmpty() && wrongPool.size >= 3) {
        "Недостаточно слов для раунда частей речи."
    }
    val correct = correctPool.random(random)
    val wrong = wrongPool.shuffled(random).take(3)
    val choices = (wrong + correct).shuffled(random)
    return PartsOfSpeechRound(
        targetKind = targetKind,
        correct = correct,
        choices = choices,
    )
}
