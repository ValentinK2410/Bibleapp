package com.example.bible.data

data class KidsSongLine(
    val text: String,
    val motion: String = "",
)

data class KidsSong(
    val id: String,
    val title: String,
    val emoji: String,
    val theme: String,
    val author: String,
    val license: String,
    val storyId: String? = null,
    val verseId: String? = null,
    val lines: List<KidsSongLine>,
    /** Имя будущего аудиофайла. Пока файла нет — строки читает голос устройства. */
    val audioAsset: String,
) {
    val chorus: List<KidsSongLine> get() = lines.filter { it.motion.isNotBlank() }.ifEmpty { lines.take(2) }
}

/**
 * Короткие песни написаны для этого приложения.
 * Чужие современные детские христианские песни сюда не копируются: на них есть права.
 */
object KidsSongs {
    val all: List<KidsSong> = listOf(
        song(
            "creation_song", "Бог создал мир", "🌍", "творение", "creation",
            listOf(
                "Бог сказал — и стал свет,",
                "солнце, звёзды в ответ.",
                "Птицы, рыбы, цветы —",
                "мир прекрасный создал Ты!",
            ),
            motions = listOf("ручки вверх", "покружись", "крылышки", "обними себя"),
        ),
        song(
            "noah_rainbow", "Радуга Ноя", "🌈", "Ной", "noah",
            listOf(
                "Тук-тук дождик по ковчегу,",
                "звери спят, не плачут, нет.",
                "Выглянуло солнце в небе —",
                "радуга, Божий обет!",
            ),
            motions = listOf("пальчики-дождик", "тихо", "ручки вверх", "дуга руками"),
        ),
        song(
            "jesus_loves", "Иисус любит детей", "🧒", "любовь", "jesus_children",
            listOf(
                "Пустите детей ко Мне,",
                "так сказал Иисус.",
                "Маленький и взрослый —",
                "каждого Он ждёт.",
            ),
            motions = listOf("шаг вперёд", "руки к сердцу", "присесть", "объятие"),
            verseId = "mt_19_14",
        ),
        song(
            "thank_you_day", "Спасибо за день", "🙏", "благодарность", null,
            listOf(
                "Спасибо за хлеб и за дом,",
                "за маму, за папу, за сон.",
                "Спасибо за свет за окном.",
                "Ты любишь нас, Бог.",
            ),
            motions = listOf("хлопок", "обними", "окошко", "поклон"),
        ),
        song(
            "easter_alive", "Он жив", "🌅", "Пасха", "easter",
            listOf(
                "Утром камень отвален,",
                "ангел говорит:",
                "«Иисуса здесь нет —",
                "Он жив, Он жив!»",
            ),
            motions = listOf("тихо", "ручки вверх", "покачать головой", "хлопки"),
        ),
        song(
            "lullaby_prayer", "Колыбельная молитва", "🌙", "вечер", null,
            listOf(
                "Ночь тиха, спи, малыш.",
                "Бог укроет твой дом.",
                "Ангел тихо стоит",
                "за твоим окном.",
            ),
            motions = listOf("покачай", "ладони домиком", "тихо", "закрой глаза"),
        ),
        song(
            "hosanna", "Осанна", "🌿", "Пасха", "easter",
            listOf(
                "Ослик тихо идёт,",
                "ветки машем, поём.",
                "Осанна, Царь идёт!",
                "Мы Его встретим с Тобой.",
            ),
            motions = listOf("шаг", "помахать", "хлопок", "помахать"),
        ),
        song(
            "shepherd", "Пастырь добрый", "🐑", "стих", "lost-sheep",
            listOf(
                "Господь — Пастырь мой,",
                "я с Ним не один.",
                "Если я потеряюсь,",
                "Он найдёт, Он найдёт.",
            ),
            motions = listOf("посох", "взяться за руки", "закрыть глаза", "найти друга"),
            verseId = "ps_22_1",
        ),
    )

    fun byId(id: String): KidsSong? = all.firstOrNull { it.id == id }
    fun forStory(storyId: String): KidsSong? = all.firstOrNull { it.storyId == storyId }
}

private fun song(
    id: String,
    title: String,
    emoji: String,
    theme: String,
    storyId: String?,
    lines: List<String>,
    motions: List<String>,
    verseId: String? = null,
) = KidsSong(
    id = id,
    title = title,
    emoji = emoji,
    theme = theme,
    author = "Текст приложения Bible",
    license = "own",
    storyId = storyId,
    verseId = verseId,
    lines = lines.mapIndexed { index, line -> KidsSongLine(line, motions.getOrElse(index) { "" }) },
    audioAsset = "kids_song_$id.m4a",
)
