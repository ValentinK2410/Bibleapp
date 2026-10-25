package com.example.bible.data

data class KidsHero(
    val id: String,
    val name: String,
    val emoji: String,
    val who: String,
    val facts: List<String>,
    val lesson: String,
    val storyIds: List<String>,
    /** Идентификатор в data/genealogy, если герой там есть. */
    val genealogyId: String? = null,
    val gradient: List<Long>,
)

object KidsHeroes {
    val all: List<KidsHero> = listOf(
        hero("adam", "Адам и Ева", "🌿", "первые люди", listOf("Жили в саду, который посадил Бог.", "Ошиблись, но Бог не бросил их.", "Бог обещал Спасителя."), "Доверять Богу.", listOf("adam_eve", "creation"), "adam", 0xFF22C55E),
        hero("noah", "Ной", "🚢", "верный и послушный", listOf("Построил ковчег.", "Взял в него семью и зверей.", "Увидел радугу обещания."), "Слушаться, даже если смеются.", listOf("noah"), "noah", 0xFF0EA5E9),
        hero("abraham", "Авраам", "⭐", "друг Божий, отец веры", listOf("Пошёл, куда сказал Бог.", "Поверил обещанию о звёздах.", "Стал папой Исаака."), "Богу можно верить.", listOf("abraham"), "abraham", 0xFF6366F1),
        hero("joseph", "Иосиф", "🌈", "прощающий брат", listOf("Братья продали его в Египет.", "Бог был с ним у фараона.", "Он простил братьев."), "Прощать.", listOf("joseph"), "joseph_son_jacob", 0xFFF59E0B),
        hero("moses", "Моисей", "🌊", "вождь народа", listOf("Малышом плыл в корзинке.", "Бог позвал его из горящего куста.", "Море расступилось перед народом."), "Бог даёт смелость и идёт рядом.", listOf("baby_moses", "burning_bush", "moses-sea", "commandments"), "moses", 0xFF0284C7),
        hero("ruth", "Руфь", "🌾", "верная", listOf("Не оставила Ноеминь.", "Собирала колоски.", "Стала прабабушкой Давида."), "Заботиться о своих.", listOf("ruth"), "ruth", 0xFFCA8A04),
        hero("samuel", "Самуил", "🌙", "слышащий Бога", listOf("Жил при доме Божьем.", "Ночью услышал своё имя.", "Ответил: «Говори, Господи»."), "Уметь слушать.", listOf("samuel"), "samuel", 0xFF4338CA),
        hero("david", "Давид", "🪨", "пастушок и царь", listOf("Пас овец и пел Богу.", "Не испугался Голиафа.", "Потом стал царём."), "С Богом можно быть смелым.", listOf("david"), "david", 0xFF16A34A),
        hero("elijah", "Илия", "🐦", "пророк", listOf("Вороны носили ему еду.", "Поток давал воду.", "У вдовы не кончались мука и масло."), "Бог кормит каждый день.", listOf("elijah_ravens"), null, 0xFF57534E),
        hero("esther", "Есфирь", "👑", "смелая царица", listOf("Стала царицей в Персии.", "Три дня молилась.", "Заступилась за свой народ."), "Молиться и делать правильное.", listOf("esther"), null, 0xFFDB2777),
        hero("daniel", "Даниил", "🦁", "молящийся", listOf("Молился три раза в день.", "Его бросили ко львам.", "Ангел сохранил его."), "Молиться можно всегда.", listOf("daniel"), null, 0xFFEA580C),
        hero("jonah", "Иона", "🐋", "вернувшийся", listOf("Сначала убежал.", "Молился в рыбе.", "Пошёл в Ниневию, и город покаялся."), "Бог даёт второй шанс.", listOf("jonah"), null, 0xFF2563EB),
        hero("mary", "Мария", "🕊️", "мама Иисуса", listOf("Поверила ангелу.", "Родила Иисуса в Вифлееме.", "Искала Его в храме, когда Ему было двенадцать."), "Доверять Богу.", listOf("annunciation", "nativity", "boy_jesus", "cana"), "mary", 0xFF818CF8),
        hero("peter", "Пётр", "🐟", "рыбак и апостол", listOf("По слову Иисуса закинул сети.", "Шёл по воде и получил руку.", "Ангел вывел его из темницы."), "Смотреть на Иисуса.", listOf("fishermen", "walk_water", "peter_prison", "pentecost"), null, 0xFF0EA5E9),
        hero("zacchaeus", "Закхей", "🌳", "изменившийся", listOf("Был маленького роста.", "Залез на смоковницу.", "Решил вернуть, что взял нечестно."), "Встреча с Иисусом меняет сердце.", listOf("zacchaeus"), null, 0xFF65A30D),
        hero("paul", "Павел", "✨", "апостол-путешественник", listOf("Раньше его звали Савл.", "На дороге в Дамаск он услышал Иисуса.", "Стал рассказывать о Нём всем."), "Бог может всё изменить.", listOf("saul_paul"), null, 0xFF4F46E5),
    )

    fun byId(id: String): KidsHero? = all.firstOrNull { it.id == id }

    fun unlocked(readStories: Set<String>): List<KidsHero> =
        all.filter { hero -> hero.storyIds.any { it in readStories } }
}

private fun hero(
    id: String,
    name: String,
    emoji: String,
    who: String,
    facts: List<String>,
    lesson: String,
    storyIds: List<String>,
    genealogyId: String?,
    color: Long,
) = KidsHero(id, name, emoji, who, facts, lesson, storyIds, genealogyId, listOf(color, 0xFFFBBF24))
