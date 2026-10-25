package com.example.bible.data

data class KidsPrayerPhrase(
    val text: String,
    val hint: String,
)

data class KidsPrayer(
    val id: String,
    val title: String,
    val emoji: String,
    val time: String,
    val lines: List<KidsPrayerPhrase>,
    /** true — текст из Синодального перевода, его не заменяют своим. */
    val scripture: Boolean = false,
    val gradient: List<Long>,
) {
    val fullText: String get() = lines.joinToString(" ") { it.text }
}

object KidsPrayers {
    val all: List<KidsPrayer> = listOf(
        KidsPrayer(
            id = "morning",
            title = "Утренняя",
            emoji = "☀️",
            time = "утро",
            gradient = listOf(0xFFFBBF24, 0xFFFB7185),
            lines = listOf(
                KidsPrayerPhrase("Господи, спасибо Тебе за новый день!", "Новый день — подарок."),
                KidsPrayerPhrase("Будь со мной сегодня.", "Бог рядом и утром."),
                KidsPrayerPhrase("Помоги мне быть добрым и послушным.", "Доброта — на весь день."),
                KidsPrayerPhrase("Аминь.", "Так мы заканчиваем молитву."),
            ),
        ),
        KidsPrayer(
            id = "evening",
            title = "Вечерняя",
            emoji = "🌙",
            time = "вечер",
            gradient = listOf(0xFF312E81, 0xFF818CF8),
            lines = listOf(
                KidsPrayerPhrase("Господи, спасибо за этот день.", "Можно вспомнить что-то хорошее."),
                KidsPrayerPhrase("Прости меня, если я сегодня кого-то обидел.", "Честно и спокойно."),
                KidsPrayerPhrase("Сохрани мою семью и дай нам спокойный сон.", "Бог бережёт дом ночью."),
                KidsPrayerPhrase("Аминь.", "Можно закрыть глаза."),
            ),
        ),
        KidsPrayer(
            id = "meal",
            title = "Перед едой",
            emoji = "🍽️",
            time = "стол",
            gradient = listOf(0xFFF97316, 0xFFFBBF24),
            lines = listOf(
                KidsPrayerPhrase("Господи, благослови эту еду и тех, кто её приготовил.", "Спасибо маме, папе и повару."),
                KidsPrayerPhrase("Спасибо Тебе за хлеб наш.", "Еда — тоже забота Бога."),
                KidsPrayerPhrase("Аминь.", "Теперь можно есть."),
            ),
        ),
        KidsPrayer(
            id = "thanks",
            title = "Благодарственная",
            emoji = "💚",
            time = "день",
            gradient = listOf(0xFF34D399, 0xFF22C55E),
            lines = listOf(
                KidsPrayerPhrase("Спасибо, Господи, за маму и папу.", "Семья."),
                KidsPrayerPhrase("Спасибо за друзей, за солнце и дождик.", "Можно добавить своё «спасибо»."),
                KidsPrayerPhrase("Ты так нас любишь!", "Бог любит."),
                KidsPrayerPhrase("Аминь.", ""),
            ),
        ),
        KidsPrayer(
            id = "our_father",
            title = "Отче наш",
            emoji = "📖",
            time = "молитва Господня",
            scripture = true,
            gradient = listOf(0xFF38BDF8, 0xFF6366F1),
            lines = listOf(
                KidsPrayerPhrase(
                    "Отче наш, сущий на небесах! да святится имя Твое;",
                    "Бог — наш Отец, Его имя свято.",
                ),
                KidsPrayerPhrase(
                    "да приидет Царствие Твое; да будет воля Твоя и на земле, как на небе;",
                    "Мы хотим, чтобы было по-Божьи и здесь.",
                ),
                KidsPrayerPhrase(
                    "хлеб наш насущный дай нам на сей день;",
                    "Хлеб насущный — всё, что нужно на сегодня.",
                ),
                KidsPrayerPhrase(
                    "и прости нам долги наши, как и мы прощаем должникам нашим;",
                    "Прости нас, и мы тоже прощаем.",
                ),
                KidsPrayerPhrase(
                    "и не введи нас в искушение, но избавь нас от лукавого.",
                    "Помоги не делать плохое и сохрани от зла.",
                ),
                KidsPrayerPhrase(
                    "Ибо Твое есть Царство и сила и слава во веки. Аминь.",
                    "Царство, сила и слава — Божьи.",
                ),
            ),
        ),
    )

    fun byId(id: String): KidsPrayer? = all.firstOrNull { it.id == id }
}
