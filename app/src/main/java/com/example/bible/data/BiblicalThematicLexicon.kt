package com.example.bible.data

/**
 * Библейская тематическая палитра: оси «свет» (сине‑голубые → зелёные) и «тьма» (красные оттенки).
 * Лексикон по формам слов (как и классическая тематика) — не полный семантический разбор текста.
 */
enum class BiblicalAxis {
    /** Бог, Христос, Дух, добро, воля, послушание и т.п. */
    LIGHT,

    /** Сатана, бесы, грех, плоть, смерть, зло и т.п. */
    DARK,
}

enum class BiblicalThematicCategory(
    val id: String,
    val labelRu: String,
    val axis: BiblicalAxis,
    /** Порядок внутри оси: меньше — раньше при перекрытии. */
    val subPriority: Int,
    val defaultColorArgb: Long,
    val wordsRu: Set<String>,
    val wordsEn: Set<String>,
) {
    GOD_ALMIGHTY(
        id = "bt_god",
        labelRu = "Бог, Творец, Всевышний",
        axis = BiblicalAxis.LIGHT,
        subPriority = 0,
        defaultColorArgb = 0xFF0D47A1,
        wordsRu = setOf(
            "бог", "бога", "богу", "богом", "боге", "боги", "богов", "богам", "богами", "богах",
            "божий", "божия", "божию", "божие", "божии",
            "божественный", "божественная", "божественное", "божественные", "божественного", "божественной",
            "всевышн", "вседержител", "всемогущ", "творец", "творца", "творен", "создател",
        ),
        wordsEn = setOf("god", "gods", "almighty", "creator", "deity", "divine", "omnipotent"),
    ),
    LORD_NAME(
        id = "bt_lord",
        labelRu = "Господь, Яхве, Иегова",
        axis = BiblicalAxis.LIGHT,
        subPriority = 1,
        defaultColorArgb = 0xFF1565C0,
        wordsRu = setOf(
            "господь", "господа", "господу", "господом", "господе", "господи", "иегова", "яхве", "адонай",
        ),
        wordsEn = setOf("lord", "jehovah", "yahweh", "adonai", "yhwh"),
    ),
    JESUS_MESSIAH(
        id = "bt_jesus",
        labelRu = "Иисус, Христос, Мессия",
        axis = BiblicalAxis.LIGHT,
        subPriority = 2,
        defaultColorArgb = 0xFF1976D2,
        wordsRu = setOf(
            "иисус", "христос", "христа", "христу", "христом", "христе", "христов", "христова",
            "мессия", "мессии", "еммануил", "наследник", "спасител", "агнец", "пастырь",
        ),
        wordsEn = setOf("jesus", "christ", "messiah", "saviour", "savior", "emmanuel", "lamb", "shepherd"),
    ),
    HOLY_SPIRIT(
        id = "bt_spirit",
        labelRu = "Дух, Святой Дух",
        axis = BiblicalAxis.LIGHT,
        subPriority = 3,
        defaultColorArgb = 0xFF00838F,
        wordsRu = setOf(
            "дух", "духа", "духу", "духом", "духе", "духов", "духам", "духами",
            "параклет", "утешител",
        ),
        wordsEn = setOf("spirit", "ghost", "paraclete", "comforter", "holy"),
    ),
    SCRIPTURE_COVENANT(
        id = "bt_scripture",
        labelRu = "Писание, завет, слово Божие",
        axis = BiblicalAxis.LIGHT,
        subPriority = 4,
        defaultColorArgb = 0xFF0277BD,
        wordsRu = setOf(
            "писание", "писания", "писанию", "завет", "завета", "завету", "заветом", "завете",
            "пророк", "пророка", "пророку", "пророком", "пророке", "пророки", "пророков",
            "закон", "закона", "закону", "законом", "законе", "повелен", "заповед", "откровен",
        ),
        wordsEn = setOf(
            "scripture", "scriptures", "covenant", "testament", "prophet", "prophets", "law", "commandment",
            "revelation",
        ),
    ),
    RIGHTEOUSNESS_WILL(
        id = "bt_will",
        labelRu = "Праведность, воля Божья, послушание",
        axis = BiblicalAxis.LIGHT,
        subPriority = 5,
        defaultColorArgb = 0xFF2E7D32,
        wordsRu = setOf(
            "правда", "правды", "правде", "правду", "правдою", "праведн", "праведник", "праведница",
            "воля", "воли", "воле", "волю", "послушан", "повиновен", "исполнен", "смирен", "смирени",
            "истин", "святость", "святости", "чистот", "непорочн",
        ),
        wordsEn = setOf(
            "righteous", "righteousness", "truth", "obey", "obedience", "humble", "humility",
            "pure", "holiness",
        ),
    ),
    GOOD_GRACE_MERCY(
        id = "bt_good",
        labelRu = "Добро, милость, благодать",
        axis = BiblicalAxis.LIGHT,
        subPriority = 6,
        defaultColorArgb = 0xFF388E3C,
        wordsRu = setOf(
            "добро", "добра", "добру", "добром", "добре", "добрый", "добрая", "доброе", "добрые",
            "милость", "милости", "милосерд", "благодать", "благодати", "благодатию", "благодатию",
            "благослов", "благословен", "щедрот", "помилован", "прощен", "прощени",
        ),
        wordsEn = setOf(
            "good", "goodness", "mercy", "grace", "bless", "blessed", "blessing", "forgive", "forgiveness",
            "kindness",
        ),
    ),
    HEAVEN_ANGELS_SALVATION(
        id = "bt_heaven",
        labelRu = "Небо, ангелы, спасение",
        axis = BiblicalAxis.LIGHT,
        subPriority = 7,
        defaultColorArgb = 0xFF43A047,
        wordsRu = setOf(
            "небо", "небес", "небеса", "небесам", "небесный", "небесная", "небесное",
            "ангел", "ангела", "ангелу", "ангелом", "ангеле", "ангелы", "ангелов", "ангелам",
            "спасен", "спасени", "спасител", "избавлен", "освобожден", "царствие", "царствия",
        ),
        wordsEn = setOf(
            "heaven", "heavenly", "angel", "angels", "salvation", "save", "saved", "kingdom", "redeem",
            "redeemed",
        ),
    ),
    SATAN_DEVIL(
        id = "bt_satan",
        labelRu = "Сатана, дьявол, диавол",
        axis = BiblicalAxis.DARK,
        subPriority = 0,
        defaultColorArgb = 0xFFB71C1C,
        wordsRu = setOf(
            "сатана", "сатаны", "сатане", "сатану", "сатаной", "диавол", "диавола", "диаволу", "диаволом",
            "дьявол", "дьявола", "лукавый", "лукавого", "лукавом", "велзевул", "велиал",
        ),
        wordsEn = setOf("satan", "devil", "beelzebub", "belial", "lucifer"),
    ),
    DEMONS(
        id = "bt_demons",
        labelRu = "Бес, бесы, нечистый дух",
        axis = BiblicalAxis.DARK,
        subPriority = 1,
        defaultColorArgb = 0xFFC62828,
        wordsRu = setOf(
            "бес", "беса", "бесу", "бесом", "бесе", "бесы", "бесов", "бесам", "бесами",
            "бесовск", "бесну", "одержим", "нечист", "нечистый", "нечистая", "нечистое",
        ),
        wordsEn = setOf("demon", "demons", "devils", "unclean", "possessed", "possession"),
    ),
    EVIL_ADVERSARY(
        id = "bt_evil",
        labelRu = "Зло, враг Божий, противник",
        axis = BiblicalAxis.DARK,
        subPriority = 2,
        defaultColorArgb = 0xFFD32F2F,
        wordsRu = setOf(
            "зло", "зла", "злу", "злом", "зле", "злой", "злая", "злое", "злые", "злодей", "злодея",
            "нечестивый", "нечестивые", "нечестивого", "нечестивых", "нечестивым", "нечестивец", "нечестивцев",
            "беззаконие", "беззакония", "безбожник", "безбожники", "враг", "врага", "врагу", "врагом", "враги",
            "противник", "противники", "противлен", "сопротивлен", "бунт", "бунтар",
        ),
        wordsEn = setOf(
            "evil", "wicked", "wickedness", "enemy", "enemies", "adversary", "ungodly", "rebel", "rebellion",
        ),
    ),
    SIN_FLESH_LUST(
        id = "bt_sin",
        labelRu = "Грех, плоть, похоть, блуд",
        axis = BiblicalAxis.DARK,
        subPriority = 3,
        defaultColorArgb = 0xFFE53935,
        wordsRu = setOf(
            "грех", "греха", "греху", "грехом", "грехе",             "грехи", "грехов", "грешник", "грешница", "грешницы", "грешниц",
            "грешит", "грешат", "согреш", "преступлен", "беззаконие", "скверн",
            "плоть", "плоти", "плотск", "плотские", "плотский", "похоть", "похоти", "похотлив",
            "блуд", "блуда", "блудник", "блудница", "блудниц", "прелюбодеян", "неверност", "разврат",
            "сладостраст", "срам", "стыд",
        ),
        wordsEn = setOf(
            "sin", "sins", "sinner", "iniquity", "transgression", "flesh", "lust", "lustful", "adultery",
            "fornication", "whore", "harlot", "lewd", "carnal",
        ),
    ),
    DEATH_VIOLENCE(
        id = "bt_death",
        labelRu = "Смерть, убийство, гибель",
        axis = BiblicalAxis.DARK,
        subPriority = 4,
        defaultColorArgb = 0xFF5D4037,
        wordsRu = setOf(
            "смерть", "смерти", "смертью", "умер", "умерл", "умира", "умирает", "умирают", "умереть",
            "убийств", "убийца", "убийцы", "убил", "убили", "заколол", "казн", "погиб", "погибл", "гибель",
            "ад", "ада", "геенн", "преисподн", "могил", "гроб", "тлен", "тление",
        ),
        wordsEn = setOf(
            "death", "dead", "die", "died", "dying", "kill", "killed", "murder", "slaughter", "hell",
            "hades", "sheol", "grave", "perish", "destruction",
        ),
    ),
    WICKED_CORRUPT(
        id = "bt_wicked",
        labelRu = "Злые люди, лицемеры, развратники",
        axis = BiblicalAxis.DARK,
        subPriority = 5,
        defaultColorArgb = 0xFFD84315,
        wordsRu = setOf(
            "лицемер", "лицемеры", "лицемеров", "лицемерств", "клеветник", "клевет", "злослов",
            "развратник", "блудник", "блудницы", "чародей", "волшеб", "идолопоклон", "идолы",
        ),
        wordsEn = setOf(
            "hypocrite", "hypocrisy", "slander", "corrupt", "corruption", "pervert", "sorcery", "witch",
            "idol", "idolatry",
        ),
    ),
    ;

    companion object {
        fun byId(id: String): BiblicalThematicCategory? = entries.find { it.id == id }
    }
}

internal fun BiblicalThematicCategory.wordsForTranslation(translation: TranslationId): Set<String> {
    val ru = wordsRu
    val en = wordsEn
    return when (translation) {
        TranslationId.WEB -> en + ru.filter { it.length <= 3 }
        else -> ru + en.filter { it.length <= 4 }
    }
}
