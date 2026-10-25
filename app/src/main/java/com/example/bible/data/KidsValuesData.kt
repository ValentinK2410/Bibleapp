package com.example.bible.data

data class KidsValueLesson(
    val id: String,
    val title: String,
    val emoji: String,
    val storyIds: List<String>,
    val question: String,
    val task: String,
    val verseId: String? = null,
    val gradient: List<Long>,
)

object KidsValues {
    val all: List<KidsValueLesson> = listOf(
        KidsValueLesson(
            "kindness", "Доброта", "💛",
            listOf("samaritan"),
            "Кому ты можешь помочь завтра?",
            "Нарисуй, как ты кому-то помог, или помоги по-настоящему.",
            verseId = "eph_4_32",
            gradient = listOf(0xFFFBBF24, 0xFFF472B6),
        ),
        KidsValueLesson(
            "honesty", "Честность", "🤍",
            listOf("zacchaeus"),
            "Почему Закхей решил вернуть деньги? Было ли тебе трудно сказать правду?",
            "Скажи сегодня одну честную фразу, даже если неловко.",
            gradient = listOf(0xFFE2E8F0, 0xFF94A3B8),
        ),
        KidsValueLesson(
            "forgiveness", "Прощение", "💚",
            listOf("joseph", "prodigal"),
            "Как поступил Иосиф с братьями? Кого нам нужно простить?",
            "Помирись с тем, на кого обиделся, или помолись о нём.",
            verseId = "eph_4_32",
            gradient = listOf(0xFF34D399, 0xFF22C55E),
        ),
        KidsValueLesson(
            "obedience", "Послушание", "💙",
            listOf("noah", "jericho"),
            "Почему Ной слушался Бога, даже когда над ним смеялись?",
            "Сделай одно поручение дома сразу и до конца.",
            gradient = listOf(0xFF38BDF8, 0xFF6366F1),
        ),
        KidsValueLesson(
            "gratitude", "Благодарность", "🧡",
            listOf("elijah_ravens"),
            "За что ты сегодня хочешь сказать «спасибо»?",
            "Скажи три «спасибо»: Богу, человеку и за что-то обычное.",
            gradient = listOf(0xFFFB923C, 0xFFFBBF24),
        ),
        KidsValueLesson(
            "courage", "Смелость", "❤️",
            listOf("david", "esther", "daniel"),
            "Когда тебе бывает страшно? Как Бог помогает быть смелым?",
            "Сделай один маленький смелый шаг: сказать правду, попросить прощения или помочь.",
            verseId = "isa_41_10",
            gradient = listOf(0xFFFB7185, 0xFFEF4444),
        ),
    )

    fun byId(id: String): KidsValueLesson? = all.firstOrNull { it.id == id }
}
