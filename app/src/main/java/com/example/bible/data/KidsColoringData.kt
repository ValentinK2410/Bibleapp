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
    /** JSON `kids-coloring/v1` в assets/kids/coloring/. */
    val assetName: String,
)

object KidsColoringPages {
    val palette: List<Pair<String, Color>> = listOf(
        "Красный" to Color(0xFFEF4444),
        "Алый" to Color(0xFFB91C1C),
        "Оранжевый" to Color(0xFFF97316),
        "Персиковый" to Color(0xFFFB923C),
        "Жёлтый" to Color(0xFFFACC15),
        "Золотой" to Color(0xFFCA8A04),
        "Лайм" to Color(0xFFA3E635),
        "Зелёный" to Color(0xFF22C55E),
        "Тёмно-зелёный" to Color(0xFF15803D),
        "Бирюзовый" to Color(0xFF14B8A6),
        "Голубой" to Color(0xFF38BDF8),
        "Синий" to Color(0xFF2563EB),
        "Тёмно-синий" to Color(0xFF1E3A8A),
        "Фиолетовый" to Color(0xFF8B5CF6),
        "Сиреневый" to Color(0xFFC084FC),
        "Розовый" to Color(0xFFF472B6),
        "Малиновый" to Color(0xFFBE185D),
        "Коричневый" to Color(0xFF92400E),
        "Бежевый" to Color(0xFFF5DEB3),
        "Серый" to Color(0xFF9CA3AF),
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
        page("abraham_1", "abraham", "Звёзды Авраама", "⭐", "kids_coloring_abraham_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.40f, 1.00f, 0.80f, Color(0xFF312E81))
            region("big_star", KidsColorShape.CIRCLE, 0.54f, 0.21f, 0.18f, 0.18f, Color(0xFFFACC15))
            region("moon", KidsColorShape.CIRCLE, 0.17f, 0.16f, 0.14f, 0.14f, Color(0xFFFACC15))
            region("ground", KidsColorShape.OVAL, 0.50f, 0.86f, 1.00f, 0.30f, Color(0xFF78350F))
            region("tent", KidsColorShape.ROUND_RECT, 0.77f, 0.64f, 0.40f, 0.28f, Color(0xFFB45309))
            region("abraham_robe", KidsColorShape.ROUND_RECT, 0.37f, 0.68f, 0.20f, 0.30f, Color(0xFF8B5CF6))
        },
        page("joseph_1", "joseph", "Разноцветная одежда Иосифа", "🌈", "kids_coloring_joseph_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.35f, 1.00f, 0.70f, Color(0xFF7DD3FC))
            region("sun", KidsColorShape.CIRCLE, 0.83f, 0.16f, 0.15f, 0.15f, Color(0xFFFACC15))
            region("ground", KidsColorShape.OVAL, 0.50f, 0.86f, 1.00f, 0.30f, Color(0xFFA3E635))
            region("coat_1", KidsColorShape.ROUND_RECT, 0.42f, 0.60f, 0.06f, 0.38f, Color(0xFFEF4444))
            region("coat_2", KidsColorShape.ROUND_RECT, 0.48f, 0.60f, 0.06f, 0.38f, Color(0xFFF97316))
            region("coat_3", KidsColorShape.ROUND_RECT, 0.54f, 0.60f, 0.06f, 0.38f, Color(0xFFFACC15))
            region("coat_4", KidsColorShape.ROUND_RECT, 0.60f, 0.60f, 0.06f, 0.38f, Color(0xFF22C55E))
            region("joseph_head", KidsColorShape.CIRCLE, 0.50f, 0.36f, 0.11f, 0.11f, Color(0xFFF5DEB3))
        },
        page("moses_sea_1", "moses-sea", "Море расступилось", "🌊", "kids_coloring_moses_sea_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.30f, 1.00f, 0.60f, Color(0xFF7DD3FC))
            region("sea_left", KidsColorShape.ROUND_RECT, 0.16f, 0.65f, 0.32f, 0.70f, Color(0xFF2563EB))
            region("sea_right", KidsColorShape.ROUND_RECT, 0.84f, 0.65f, 0.32f, 0.70f, Color(0xFF2563EB))
            region("dry_path", KidsColorShape.ROUND_RECT, 0.50f, 0.78f, 0.36f, 0.44f, Color(0xFFF5DEB3))
            region("moses_robe", KidsColorShape.ROUND_RECT, 0.50f, 0.64f, 0.20f, 0.28f, Color(0xFFB91C1C))
            region("pillar", KidsColorShape.OVAL, 0.50f, 0.20f, 0.30f, 0.14f, Color(0xFFCBD5E1))
        },
        page("daniel_1", "daniel", "Даниил и львы", "🦁", "kids_coloring_daniel_1.json") {
            region("night", KidsColorShape.ROUND_RECT, 0.50f, 0.40f, 1.00f, 0.80f, Color(0xFF312E81))
            region("window_pane", KidsColorShape.ROUND_RECT, 0.50f, 0.36f, 0.30f, 0.12f, Color(0xFFFACC15))
            region("floor", KidsColorShape.ROUND_RECT, 0.50f, 0.88f, 1.00f, 0.24f, Color(0xFF92400E))
            region("daniel_robe", KidsColorShape.ROUND_RECT, 0.50f, 0.68f, 0.20f, 0.30f, Color(0xFF14B8A6))
            region("lion1_mane", KidsColorShape.CIRCLE, 0.83f, 0.78f, 0.14f, 0.14f, Color(0xFF92400E))
            region("angel_robe", KidsColorShape.ROUND_RECT, 0.19f, 0.70f, 0.16f, 0.28f, Color(0xFFFFFBF5))
        },
        page("jonah_1", "jonah", "Иона и большая рыба", "🐋", "kids_coloring_jonah_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.28f, 1.00f, 0.56f, Color(0xFF7DD3FC))
            region("sea", KidsColorShape.ROUND_RECT, 0.50f, 0.78f, 1.00f, 0.44f, Color(0xFF2563EB))
            region("fish_body", KidsColorShape.OVAL, 0.59f, 0.75f, 0.50f, 0.30f, Color(0xFF14B8A6))
            region("fish_belly", KidsColorShape.OVAL, 0.54f, 0.81f, 0.35f, 0.15f, Color(0xFFFFFBF5))
            region("boat", KidsColorShape.ROUND_RECT, 0.22f, 0.52f, 0.27f, 0.10f, Color(0xFFB45309))
            region("sail", KidsColorShape.ROUND_RECT, 0.30f, 0.38f, 0.13f, 0.18f, Color(0xFFFDE68A))
        },
        page("five_loaves_1", "five-loaves", "Пять хлебов и две рыбки", "🍞", "kids_coloring_five_loaves_1.json") {
            region("sky", KidsColorShape.ROUND_RECT, 0.50f, 0.35f, 1.00f, 0.70f, Color(0xFF7DD3FC))
            region("grass", KidsColorShape.OVAL, 0.50f, 0.86f, 1.00f, 0.30f, Color(0xFFA3E635))
            region("blanket", KidsColorShape.ROUND_RECT, 0.52f, 0.93f, 0.76f, 0.14f, Color(0xFFEF4444))
            region("basket_band1", KidsColorShape.ROUND_RECT, 0.52f, 0.82f, 0.19f, 0.12f, Color(0xFFB45309))
            region("loaf1", KidsColorShape.OVAL, 0.46f, 0.74f, 0.10f, 0.07f, Color(0xFFB45309))
            region("loaf2", KidsColorShape.OVAL, 0.56f, 0.73f, 0.10f, 0.07f, Color(0xFFB45309))
            region("fish1_body", KidsColorShape.OVAL, 0.29f, 0.96f, 0.11f, 0.05f, Color(0xFF38BDF8))
        },
    )

    fun byId(id: String): KidsColorPage? = pages.firstOrNull { it.id == id }
    fun forStory(storyId: String): List<KidsColorPage> = pages.filter { it.storyId == storyId }

    /** Ближайший цвет набора к произвольному образцу — чтобы подсказку можно было назвать и повторить. */
    fun nearestPalette(argb: Int): Pair<String, Color> {
        val target = Color(argb)
        return palette.minByOrNull { (_, swatch) ->
            val dr = target.red - swatch.red
            val dg = target.green - swatch.green
            val db = target.blue - swatch.blue
            dr * dr + dg * dg + db * db
        } ?: palette.first()
    }
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
