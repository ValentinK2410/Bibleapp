package com.example.bible.data

/**
 * Библейские карты с [Wikimedia Commons](https://commons.wikimedia.org) (CC BY-SA / общественное достояние).
 * Основной набор — с русскоязычными подписями (локализации и классические атласы); отдельные схемы с английской легендой помечены в названии.
 * Прямые ссылки на файлы; для офлайн — копия в [com.example.bible.data.BibleMapsStorage].
 */
enum class BibleMapCategory {
    OLD_TESTAMENT,
    NEW_TESTAMENT,
}

data class BibleMapDefinition(
    val id: String,
    val titleRu: String,
    val category: BibleMapCategory,
    /** Прямой URL изображения (PNG, JPEG или SVG). */
    val remoteUrl: String,
    /** Краткая атрибуция для экрана «О карте». */
    val attributionRu: String,
)

object BibleMapsCatalog {
    val all: List<BibleMapDefinition> = listOf(
        BibleMapDefinition(
            id = "ot_israel_judah_830",
            titleRu = "Царства Израиля и Иудеи (~830 г. до н.э.)",
            category = BibleMapCategory.OLD_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/7/7b/Kingdoms_of_Israel_and_Judah_map_830-ru.svg",
            attributionRu = "Bums, CC BY-SA 4.0 · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "ot_ancient_egypt_ru",
            titleRu = "Древний Египет (города и раскопки, рус.)",
            category = BibleMapCategory.OLD_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/c/ca/Ancient_Egypt_map-ru.svg",
            attributionRu = "Jeff Dahl / Anton Gutsunaev, CC BY-SA 3.0 · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "ot_exodus",
            titleRu = "Путь исхода из Египта (схема, англ. подписи)",
            category = BibleMapCategory.OLD_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/5/5b/Exodus_Map.jpg",
            attributionRu = "Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "ot_assyrian_700",
            titleRu = "Ассирийская держава (~654 г. до н.э., рус.)",
            category = BibleMapCategory.OLD_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c0/Assyria_map_ru.svg",
            attributionRu = "Anton Gutsunaev, CC BY-SA 3.0 · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "ot_persian_490",
            titleRu = "Империя Ахеменидов (конец VI в. до н.э., рус.)",
            category = BibleMapCategory.OLD_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/5/58/Achaemenid_Empire_ru.svg",
            attributionRu = "Anton Gutsunaev, CC BY-SA 3.0 · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "nt_palestine_jesus",
            titleRu = "Палестина (историческая карта с русскими подписями)",
            category = BibleMapCategory.NEW_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2a/Map_of_Palestine_in_Russian.png",
            attributionRu = "общественное достояние (Российская империя до 1917 г.) · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "nt_jerusalem_jesus",
            titleRu = "Древний Иерусалим и план 1900 г. (Брокгауз и Ефрон, рус.)",
            category = BibleMapCategory.NEW_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c8/Old_Jerusalem_map.jpg",
            attributionRu = "Брокгауз и Ефрон, общественное достояние · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "nt_roman_empire_117_ru",
            titleRu = "Римская империя при максимальном расширении (117 г., рус.)",
            category = BibleMapCategory.NEW_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/5/58/RomanEmpire_117_ru.svg",
            attributionRu = "FushPhoenix, CC0 · Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "nt_paul_journeys",
            titleRu = "Миссионерские путешествия ап. Павла (легенда на англ.)",
            category = BibleMapCategory.NEW_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Biblica_Open_Bible_Map_16_17_Paul_missionary_journeys_map.png/2000px-Biblica_Open_Bible_Map_16_17_Paul_missionary_journeys_map.png",
            attributionRu = "Biblica / Wikimedia Commons",
        ),
        BibleMapDefinition(
            id = "nt_paul_journeys_color",
            titleRu = "Путешествия Павла, цветная (легенда на англ.)",
            category = BibleMapCategory.NEW_TESTAMENT,
            remoteUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Biblica_Open_Bible_Map_COLOR_18_Paul%27s_Journeys.png/2000px-Biblica_Open_Bible_Map_COLOR_18_Paul%27s_Journeys.png",
            attributionRu = "Biblica / Wikimedia Commons",
        ),
    )

    fun byCategory(cat: BibleMapCategory): List<BibleMapDefinition> =
        all.filter { it.category == cat }

    fun byId(id: String): BibleMapDefinition? = all.find { it.id == id }
}
