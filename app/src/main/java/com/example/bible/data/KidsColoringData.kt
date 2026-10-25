package com.example.bible.data

import androidx.compose.ui.graphics.Color

enum class KidsColorShape { CIRCLE, OVAL, ROUND_RECT }

data class KidsColorRegion(
    val id: String,
    val shape: KidsColorShape,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val hint: Color,
)

data class KidsColorPage(
    val id: String,
    val storyId: String,
    val title: String,
    val emoji: String,
    val regions: List<KidsColorRegion>,
    /** Настоящий контур ещё не нарисован. Имя файла, который можно добавить позже. */
    val assetName: String,
)

object KidsColoringPages {
    val palette: List<Pair<String, Color>> = listOf(
        "Красный" to Color(0xFFEF4444),
        "Оранжевый" to Color(0xFFF97316),
        "Жёлтый" to Color(0xFFFACC15),
        "Зелёный" to Color(0xFF22C55E),
        "Бирюзовый" to Color(0xFF14B8A6),
        "Голубой" to Color(0xFF38BDF8),
        "Синий" to Color(0xFF2563EB),
        "Фиолетовый" to Color(0xFF8B5CF6),
        "Розовый" to Color(0xFFF472B6),
        "Коричневый" to Color(0xFF92400E),
        "Белый" to Color(0xFFFFFBF5),
        "Чёрный" to Color(0xFF1F2937),
    )

    val pages: List<KidsColorPage> = listOf(
        page("creation_1", "creation", "Бог создал мир", "🌍", "kids_coloring_creation_1.json") {
            region("sun", KidsColorShape.CIRCLE, 0.72f, 0.28f, 0.16f, 0.16f, Color(0xFFFACC15))
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.32f, 0.92f, 0.50f, Color(0xFF7DD3FC))
            region("land", KidsColorShape.OVAL, 0.50f, 0.78f, 0.90f, 0.28f, Color(0xFF86EFAC))
        },
        page("noah_1", "noah", "Ной и ковчег", "🚢", "kids_coloring_noah_1.json") {
            region("hull", KidsColorShape.ROUND_RECT, 0.50f, 0.62f, 0.70f, 0.22f, Color(0xFFB45309))
            region("cabin", KidsColorShape.ROUND_RECT, 0.50f, 0.46f, 0.28f, 0.16f, Color(0xFFFDE68A))
            region("wave", KidsColorShape.OVAL, 0.50f, 0.82f, 0.92f, 0.16f, Color(0xFF38BDF8))
            region("rainbow", KidsColorShape.OVAL, 0.50f, 0.28f, 0.55f, 0.22f, Color(0xFFF472B6))
        },
        page("nativity_1", "nativity", "Рождество", "⭐", "kids_coloring_nativity_1.json") {
            region("star", KidsColorShape.CIRCLE, 0.50f, 0.18f, 0.12f, 0.12f, Color(0xFFFACC15))
            region("stable", KidsColorShape.ROUND_RECT, 0.50f, 0.62f, 0.62f, 0.40f, Color(0xFFD6B48A))
            region("manger", KidsColorShape.ROUND_RECT, 0.50f, 0.70f, 0.28f, 0.12f, Color(0xFFFBBF24))
        },
        page("easter_1", "easter", "Пустая гробница", "🌅", "kids_coloring_easter_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.40f, 0.96f, 0.70f, Color(0xFFFDE68A))
            region("tomb", KidsColorShape.ROUND_RECT, 0.48f, 0.62f, 0.40f, 0.36f, Color(0xFFCBD5E1))
            region("stone", KidsColorShape.CIRCLE, 0.70f, 0.68f, 0.16f, 0.16f, Color(0xFF94A3B8))
            region("sun", KidsColorShape.CIRCLE, 0.22f, 0.24f, 0.14f, 0.14f, Color(0xFFFBBF24))
        },
        page("david_1", "david", "Давид и праща", "🪨", "kids_coloring_david_1.json") {
            region("hill", KidsColorShape.OVAL, 0.50f, 0.78f, 0.90f, 0.24f, Color(0xFF86EFAC))
            region("david", KidsColorShape.CIRCLE, 0.32f, 0.58f, 0.16f, 0.16f, Color(0xFFFBBF24))
            region("stone", KidsColorShape.CIRCLE, 0.48f, 0.50f, 0.06f, 0.06f, Color(0xFF94A3B8))
        },
        page("sheep_1", "lost-sheep", "Потерянная овечка", "🐑", "kids_coloring_lost_sheep_1.json") {
            region("meadow", KidsColorShape.OVAL, 0.50f, 0.78f, 0.92f, 0.26f, Color(0xFF4ADE80))
            region("body", KidsColorShape.OVAL, 0.50f, 0.58f, 0.36f, 0.22f, Color(0xFFFFFBF5))
            region("head", KidsColorShape.CIRCLE, 0.68f, 0.50f, 0.14f, 0.14f, Color(0xFFFFFBF5))
        },
    )

    fun byId(id: String): KidsColorPage? = pages.firstOrNull { it.id == id }
    fun forStory(storyId: String): List<KidsColorPage> = pages.filter { it.storyId == storyId }
}

private class RegionList {
    val items = mutableListOf<KidsColorRegion>()
    fun region(id: String, shape: KidsColorShape, cx: Float, cy: Float, w: Float, h: Float, hint: Color) {
        items += KidsColorRegion(id, shape, cx, cy, w, h, hint)
    }
}

private fun page(
    id: String,
    storyId: String,
    title: String,
    emoji: String,
    assetName: String,
    block: RegionList.() -> Unit,
): KidsColorPage {
    val list = RegionList().apply(block)
    return KidsColorPage(id, storyId, title, emoji, list.items, assetName)
}
