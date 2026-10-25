package com.example.bible.data

data class KidsMapPlace(
    val id: String,
    val name: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val storyIds: List<String>,
    val blurb: String,
)

data class KidsMapRoute(
    val id: String,
    val title: String,
    val placeIds: List<String>,
    val color: Long,
)

object KidsBibleMap {
    val places: List<KidsMapPlace> = listOf(
        place("ur", "Ур", "⛺", 0.78f, 0.62f, listOf("abraham"), "Отсюда Авраам когда-то ушёл по слову Бога."),
        place("canaan", "Ханаан", "🏕️", 0.46f, 0.48f, listOf("abraham", "joseph"), "Земля, которую Бог обещал Аврааму."),
        place("egypt", "Египет", "🏺", 0.28f, 0.62f, listOf("joseph", "baby_moses", "burning_bush"), "Здесь жили Иосиф и малыш Моисей."),
        place("red_sea", "Красное море", "🌊", 0.32f, 0.52f, listOf("moses-sea"), "Море расступилось, и народ перешёл."),
        place("sinai", "Гора Синай", "⛰️", 0.34f, 0.58f, listOf("commandments", "burning_bush"), "Здесь Бог говорил с Моисеем."),
        place("jericho", "Иерихон", "🎺", 0.48f, 0.46f, listOf("jericho", "zacchaeus"), "Упали стены. Позже здесь Закхей залез на дерево."),
        place("bethlehem", "Вифлеем", "⭐", 0.47f, 0.50f, listOf("nativity", "ruth"), "Здесь родился Иисус. Отсюда родом Руфь и Давид."),
        place("nazareth", "Назарет", "🏠", 0.44f, 0.36f, listOf("annunciation", "boy_jesus"), "Город Марии. Здесь Иисус рос."),
        place("galilee", "Галилейское море", "🐟", 0.48f, 0.34f, listOf("storm", "walk_water", "fishermen"), "Буря, хождение по воде и чудесный улов."),
        place("capernaum", "Капернаум", "🛏️", 0.49f, 0.32f, listOf("paralytic"), "Друзья опустили больного через крышу."),
        place("jerusalem", "Иерусалим", "🌅", 0.48f, 0.48f, listOf("easter", "boy_jesus", "pentecost"), "Пасха, храм и день, когда сошёл Дух."),
        place("nineveh", "Ниневия", "🐋", 0.70f, 0.28f, listOf("jonah"), "Город, куда наконец пошёл Иона."),
        place("babylon", "Вавилон", "🗼", 0.72f, 0.55f, listOf("babel", "daniel"), "Башня и позже львиный ров Даниила."),
        place("damascus", "Дамаск", "✨", 0.52f, 0.40f, listOf("saul_paul"), "По дороге сюда Савл услышал Иисуса."),
    )

    val routes: List<KidsMapRoute> = listOf(
        KidsMapRoute("abraham", "Путь Авраама", listOf("ur", "canaan"), 0xFFF59E0B),
        KidsMapRoute("exodus", "Исход из Египта", listOf("egypt", "red_sea", "sinai", "jericho"), 0xFF0EA5E9),
        KidsMapRoute("jonah", "Путь Ионы", listOf("galilee", "nineveh"), 0xFF6366F1),
        KidsMapRoute("paul", "Дорога Павла", listOf("jerusalem", "damascus"), 0xFFEC4899),
    )

    fun place(id: String): KidsMapPlace? = places.firstOrNull { it.id == id }

    val whereWasJesusBorn: KidsMapPlace = place("bethlehem")!!
}

private fun place(
    id: String,
    name: String,
    emoji: String,
    x: Float,
    y: Float,
    storyIds: List<String>,
    blurb: String,
) = KidsMapPlace(id, name, emoji, x, y, storyIds, blurb)
