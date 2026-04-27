package com.example.bible.data.genealogy

/**
 * Справочник персон и связей (упрощённые родословия по ВЗ и Мф 1 / Лк 3).
 * В исторических списках бывают пропуски имён — это отражено в примечаниях.
 */
object GenealogyData {

    val persons: Map<String, GenealogyPerson> = buildMap {
        fun p(
            id: String,
            name: String,
            sex: GenealogySex,
            note: String,
            vararg refs: String,
        ) {
            put(id, GenealogyPerson(id, name, sex, note, refs.toList()))
        }
        // Праотцы (Быт 5)
        p("adam", "Адам", GenealogySex.M, "Первый человек; праотец рода.", "Быт 5:1–5")
        p("eve", "Ева", GenealogySex.F, "Первая женщина; «мать всех живущих».", "Быт 3:20")
        p("seth", "Сиф", GenealogySex.M, "Сын Адама, через него пошла линия к Ною.", "Быт 5:3")
        p("enosh", "Енос", GenealogySex.M, "", "Быт 5:6")
        p("kenan", "Каинан", GenealogySex.M, "", "Быт 5:9")
        p("mahalalel", "Малелеил", GenealogySex.M, "", "Быт 5:12")
        p("jared", "Иаред", GenealogySex.M, "", "Быт 5:15")
        p("enoch", "Енох", GenealogySex.M, "«Ходил пред Богом»; взят Богом.", "Быт 5:21–24")
        p("methuselah", "Мафусал", GenealogySex.M, "Долгожитель.", "Быт 5:25")
        p("lamech", "Ламех", GenealogySex.M, "Отец Ноя.", "Быт 5:28")
        p("noah", "Ной", GenealogySex.M, "Спасён от потопа; праотец нового мира.", "Быт 6–9")
        p("shem", "Сим", GenealogySex.M, "Сын Ноя; через него семитские народы.", "Быт 9:18")
        p("ham", "Хам", GenealogySex.M, "Сын Ноя.", "Быт 9:18")
        p("japheth", "Иафет", GenealogySex.M, "Сын Ноя.", "Быт 9:18")
        // Быт 11 — Сим → Авраам
        p("arpachshad", "Арфаксад", GenealogySex.M, "", "Быт 11:10")
        p("shelah", "Сала", GenealogySex.M, "", "Быт 11:12")
        p("eber", "Евер", GenealogySex.M, "Имя «еврей» связано с родом Евера.", "Быт 11:14")
        p("peleg", "Фалег", GenealogySex.M, "При нём разделена земля.", "Быт 11:16")
        p("reu", "Рагав", GenealogySex.M, "", "Быт 11:18")
        p("serug", "Серух", GenealogySex.M, "", "Быт 11:20")
        p("nahor_serug", "Нахор", GenealogySex.M, "Отец Фарры (Таре).", "Быт 11:22")
        p("terah", "Фарра (Таре)", GenealogySex.M, "Отец Авраама, Нахора и Аррана.", "Быт 11:24")
        p("haran", "Арран", GenealogySex.M, "Отец Лота; умер в Ур-Халдейском.", "Быт 11:27–28")
        p("nahor_abraham_brother", "Нахор", GenealogySex.M, "Брат Авраама; родоначальник части арамеев.", "Быт 11:26")
        p("abraham", "Авраам", GenealogySex.M, "Отец веры; завет с Богом.", "Быт 12–25")
        p("sarah", "Сарра", GenealogySex.F, "Жена Авраама; мать Исаака.", "Быт 17:15")
        p("hagar", "Агарь", GenealogySex.F, "Рабыня Сарры; мать Измаила.", "Быт 16")
        p("ishmael", "Измаил", GenealogySex.M, "Сын Авраама от Агари.", "Быт 16; 21")
        p("isaac", "Исаак", GenealogySex.M, "Сын обетования; отец Иакова и Исава.", "Быт 21")
        p("rebekah", "Ревекка", GenealogySex.F, "Жена Исаака.", "Быт 24")
        p("esau", "Исав", GenealogySex.M, "Старший брат Иакова; родоначальник едомлян.", "Быт 25")
        p("jacob", "Иаков (Израиль)", GenealogySex.M, "Патриарх двенадцати колен; боролся с Богом.", "Быт 25–49")
        p("leah", "Лия", GenealogySex.F, "Жена Иакова; мать многих сыновей.", "Быт 29")
        p("rachel", "Рахиль", GenealogySex.F, "Любимая жена Иакова; мать Иосифа и Вениамина.", "Быт 29–35")
        p("bilhah", "Валла", GenealogySex.F, "Рабыня Рахили.", "Быт 29")
        p("zilpah", "Зелфа", GenealogySex.F, "Рабыня Лии.", "Быт 29")
        // Сыновья Иакова (кратко)
        p("reuben", "Рувим", GenealogySex.M, "Первенец Иакова.", "Быт 35:23")
        p("simeon", "Симеон", GenealogySex.M, "", "Быт 35:23")
        p("levi", "Левий", GenealogySex.M, "Колено священников; праотец Моисея и Аарона.", "Быт 35:23")
        p("judah", "Иуда", GenealogySex.M, "Колено царей; линия к Давиду.", "Быт 35:23")
        p("issachar", "Иссахар", GenealogySex.M, "", "Быт 35:23")
        p("zebulun", "Завулон", GenealogySex.M, "", "Быт 35:23")
        p("dan", "Дан", GenealogySex.M, "", "Быт 35:25")
        p("naphtali", "Неффалим", GenealogySex.M, "", "Быт 35:25")
        p("gad", "Гад", GenealogySex.M, "", "Быт 35:26")
        p("asher", "Асир", GenealogySex.M, "", "Быт 35:26")
        p("joseph_son_jacob", "Иосиф", GenealogySex.M, "Сын Иакова и Рахили; спаситель семьи в Египте (не Иосиф обручник).", "Быт 37–50")
        p("benjamin", "Вениамин", GenealogySex.M, "Младший сын Иакова.", "Быт 35:18")
        p("dinah", "Дина", GenealogySex.F, "Дочь Иакова и Лии.", "Быт 34")
        // Лот и Моав
        p("lot", "Лот", GenealogySex.M, "Племянник Авраама; спасён от Содома.", "Быт 11–19")
        p("moab", "Моав", GenealogySex.M, "Родоначальник моавитян (от дочери Лота).", "Быт 19:37")
        // Иуда → Давид (Руфь 4; 1 Пар 2)
        p("er", "Ер", GenealogySex.M, "Первый сын Иуды от хананеянки.", "Быт 38")
        p("onan", "Онан", GenealogySex.M, "Второй сын Иуды.", "Быт 38")
        p("shelah_judah", "Села", GenealogySex.M, "Третий сын Иуды.", "Быт 38")
        p("tamar", "Фамарь", GenealogySex.F, "Невестка Иуды; мать Фареса и Зары.", "Быт 38")
        p("perez", "Фарес", GenealogySex.M, "Предок Давида через линию Иуды.", "Руфь 4:12")
        p("zerah", "Зара", GenealogySex.M, "Брат-близнец Фареса.", "Быт 38")
        p("hezron", "Есром", GenealogySex.M, "", "Руфь 4:18")
        p("ram", "Арам", GenealogySex.M, "", "Руфь 4:19")
        p("amminadab", "Аминадав", GenealogySex.M, "", "Руфь 4:19")
        p("nahshon", "Наассон", GenealogySex.M, "", "Руфь 4:20")
        p("salmon", "Салмон", GenealogySex.M, "", "Руфь 4:20")
        p("boaz", "Вооз", GenealogySex.M, "Свёкор Руфи; прадед Давида.", "Руфь 2–4")
        p("ruth", "Руфь", GenealogySex.F, "Моавитянка; прабабка Давида.", "Руфь 1–4")
        p("obed", "Овид", GenealogySex.M, "Сын Вооза и Руфи; дед Иессея.", "Руфь 4:17")
        p("jesse", "Иессей", GenealogySex.M, "Отец Давида.", "Руфь 4:17")
        p("david", "Давид", GenealogySex.M, "Царь Израиля и Иудеи; «по плоти» предок Мессии.", "1 Цар 16; Мф 1")
        p("bathsheba", "Вирсавия", GenealogySex.F, "Жена Урии; мать Соломона.", "2 Цар 11–12")
        p("solomon", "Соломон", GenealogySex.M, "Сын Давида; храм; линия Мф 1.", "3 Цар 1–11")
        p("nathan_son_david", "Натан", GenealogySex.M, "Сын Давода; ветвь родословия Лк 3 (не пророк Натан).", "2 Цар 5:14")
        // Упрощённая линия царей Иудеи к плену (Мф 1 сокращённо)
        p("rehoboam", "Ровоам", GenealogySex.M, "Сын Соломона.", "3 Цар 11:43")
        p("abijah", "Авия", GenealogySex.M, "", "3 Цар 15:1")
        p("asa", "Аса", GenealogySex.M, "", "3 Цар 15:8")
        p("jehoshaphat", "Иосафат", GenealogySex.M, "", "3 Цар 15:24")
        p("uzziah", "Озия", GenealogySex.M, "Также Азария.", "4 Цар 15:1")
        p("hezekiah", "Езекия", GenealogySex.M, "", "4 Цар 18")
        p("manasseh", "Манассия", GenealogySex.M, "", "4 Цар 21")
        p("josiah", "Иосия", GenealogySex.M, "", "4 Цар 22")
        p("jeconiah", "Иехония", GenealogySex.M, "Последний царь перед вавилонским пленом (в родословии Мф).", "4 Цар 24")
        p("shealtiel", "Салафииль", GenealogySex.M, "", "1 Пар 3:17")
        p("zerubbabel", "Зоровавель", GenealogySex.M, "Руководитель возвращения из плена.", "Ездра 3")
        p("abiud", "Авиуд", GenealogySex.M, "", "Мф 1:13")
        p("matthan", "Матфан", GenealogySex.M, "", "Мф 1:15")
        p("jacob_father_joseph", "Иаков", GenealogySex.M, "Отец Иосифа обручника (не патриарх Иаков).", "Мф 1:15")
        p("joseph_husband", "Иосиф", GenealogySex.M, "Обручник Марии; законный отец Иисуса по Мф 1.", "Мф 1:16")
        p("mary", "Мария", GenealogySex.F, "Матерь Господа Иисуса Христа.", "Мф 1; Лк 1")
        p("jesus", "Иисус Христос", GenealogySex.M, "Сын Божий и Марии; Сын Давидов по плоти (Рим 1:3).", "Мф 1:21")
        // Упрощённая ветвь Лк 3 к Марии (Натан — не Соломон)
        p("matthat_luke", "Матфат", GenealogySex.M, "Упрощённое звено ветви Лк 3 (к Марии).", "Лк 3:24")
        p("eli_mary", "Илий", GenealogySex.M, "В родословии Лк 3 — предок Иосифа/Марии (толкования различаются).", "Лк 3:23")
        // Левит, Моисей, Аарон
        p("kohath", "Кааф", GenealogySex.M, "Сын Левия.", "Исх 6:16")
        p("amram", "Амрам", GenealogySex.M, "Отец Моисея, Аарона и Мириам.", "Исх 6:20")
        p("miriam", "Мариам", GenealogySex.F, "Сестра Моисея.", "Исх 15:20")
        p("aaron", "Аарон", GenealogySex.M, "Первосвященник.", "Исх 4:14")
        p("moses", "Моисей", GenealogySex.M, "Законодатель; вождь исхода.", "Исх 2")
        // Самуил, Саул
        p("elkanah", "Елкана", GenealogySex.M, "Отец Самуила.", "1 Цар 1")
        p("hannah", "Анна", GenealogySex.F, "Мать Самуила.", "1 Цар 1")
        p("samuel", "Самуил", GenealogySex.M, "Судья и пророк; помазал Саула и Давида.", "1 Цар 1–16")
        p("kish", "Кис", GenealogySex.M, "Отец Саула.", "1 Цар 9")
        p("saul", "Саул", GenealogySex.M, "Первый царь Израиля.", "1 Цар 9–31")
    }

    val parentChildEdges: List<ParentChildEdge> = buildList {
        fun pc(p: String, c: String, k: ParentLinkKind = ParentLinkKind.BIOLOGICAL) {
            add(ParentChildEdge(p, c, k))
        }
        // Адам — Сиф
        pc("adam", "seth")
        // Сиф — Ной
        pc("seth", "enosh"); pc("enosh", "kenan"); pc("kenan", "mahalalel"); pc("mahalalel", "jared")
        pc("jared", "enoch"); pc("enoch", "methuselah"); pc("methuselah", "lamech"); pc("lamech", "noah")
        // Ной — сыновья
        pc("noah", "shem"); pc("noah", "ham"); pc("noah", "japheth")
        // Сим — Таре
        pc("shem", "arpachshad"); pc("arpachshad", "shelah"); pc("shelah", "eber"); pc("eber", "peleg")
        pc("peleg", "reu"); pc("reu", "serug"); pc("serug", "nahor_serug"); pc("nahor_serug", "terah")
        pc("terah", "abraham"); pc("terah", "nahor_abraham_brother"); pc("terah", "haran")
        pc("haran", "lot")
        // Авраам
        pc("abraham", "isaac"); pc("sarah", "isaac")
        pc("abraham", "ishmael"); pc("hagar", "ishmael")
        pc("isaac", "esau"); pc("isaac", "jacob"); pc("rebekah", "esau"); pc("rebekah", "jacob")
        // Иаков — двенадцать колен
        pc("jacob", "reuben"); pc("leah", "reuben")
        pc("jacob", "simeon"); pc("leah", "simeon")
        pc("jacob", "levi"); pc("leah", "levi")
        pc("jacob", "judah"); pc("leah", "judah")
        pc("jacob", "issachar"); pc("leah", "issachar")
        pc("jacob", "zebulun"); pc("leah", "zebulun")
        pc("jacob", "dinah"); pc("leah", "dinah")
        pc("jacob", "dan"); pc("bilhah", "dan")
        pc("jacob", "naphtali"); pc("bilhah", "naphtali")
        pc("jacob", "gad"); pc("zilpah", "gad")
        pc("jacob", "asher"); pc("zilpah", "asher")
        pc("jacob", "joseph_son_jacob"); pc("rachel", "joseph_son_jacob")
        pc("jacob", "benjamin"); pc("rachel", "benjamin")
        // Иуда — Фарес
        pc("judah", "er"); pc("judah", "onan"); pc("judah", "shelah_judah")
        pc("judah", "perez"); pc("tamar", "perez")
        pc("judah", "zerah"); pc("tamar", "zerah")
        pc("perez", "hezron"); pc("hezron", "ram"); pc("ram", "amminadab"); pc("amminadab", "nahshon")
        pc("nahshon", "salmon"); pc("salmon", "boaz"); pc("boaz", "obed"); pc("ruth", "obed")
        pc("obed", "jesse"); pc("jesse", "david")
        // Давид
        pc("david", "solomon"); pc("bathsheba", "solomon")
        pc("david", "nathan_son_david")
        // Цари (упрощ.)
        pc("solomon", "rehoboam"); pc("rehoboam", "abijah"); pc("abijah", "asa"); pc("asa", "jehoshaphat")
        pc("jehoshaphat", "uzziah"); pc("uzziah", "hezekiah"); pc("hezekiah", "manasseh"); pc("manasseh", "josiah")
        pc("josiah", "jeconiah"); pc("jeconiah", "shealtiel"); pc("shealtiel", "zerubbabel")
        pc("zerubbabel", "abiud"); pc("abiud", "matthan"); pc("matthan", "jacob_father_joseph")
        pc("jacob_father_joseph", "joseph_husband")
        // Мария и Иисус
        pc("eli_mary", "mary")
        pc("mary", "jesus", ParentLinkKind.BIOLOGICAL)
        pc("joseph_husband", "jesus", ParentLinkKind.LEGAL)
        // Лк 3 — упрощённая ветвь к Илию (параллель Соломону)
        pc("david", "nathan_son_david")
        pc("nathan_son_david", "matthat_luke"); pc("matthat_luke", "eli_mary")
        // Левий — Моисей (родители Иаков+Лия уже выше)
        pc("levi", "kohath"); pc("kohath", "amram")
        pc("amram", "moses"); pc("amram", "aaron"); pc("amram", "miriam")
        // Самуил, Саул
        pc("elkanah", "samuel"); pc("hannah", "samuel")
        pc("kish", "saul")
        // Лот — Моав (родоначальник)
        pc("lot", "moab")
    }

    /** Пары супругов (для поиска «родственников» и смешанных путей). */
    val spousePairs: List<Pair<String, String>> = listOf(
        "adam" to "eve",
        "abraham" to "sarah",
        "isaac" to "rebekah",
        "jacob" to "leah",
        "jacob" to "rachel",
        "boaz" to "ruth",
        "david" to "bathsheba",
        "joseph_husband" to "mary",
        "elkanah" to "hannah",
    )

    val themes: List<GenealogyTheme> = listOf(
        GenealogyTheme(
            id = "adam_to_abraham",
            titleRu = "От Адама к Аврааму",
            descriptionRu = "Линия Сифа, Сима и Таре — к отцу веры.",
            startPersonId = "adam",
            highlightIds = listOf("adam", "noah", "shem", "terah", "abraham"),
        ),
        GenealogyTheme(
            id = "to_david",
            titleRu = "К Давиду через Иуду",
            descriptionRu = "Фарес, Вооз, Руфь, Иессей — к царю и псалмопевцу.",
            startPersonId = "judah",
            highlightIds = listOf("judah", "perez", "boaz", "ruth", "david"),
        ),
        GenealogyTheme(
            id = "messiah_line",
            titleRu = "Мессия: Давид и Иисус",
            descriptionRu = "Соломонова линия в Мф 1 и связь с Давидом.",
            startPersonId = "david",
            highlightIds = listOf("david", "solomon", "joseph_husband", "mary", "jesus"),
        ),
        GenealogyTheme(
            id = "ruth",
            titleRu = "Руфь и Вооз",
            descriptionRu = "Моавитянка в родословии царя Давида.",
            startPersonId = "ruth",
            highlightIds = listOf("ruth", "boaz", "obed", "jesse", "david"),
        ),
        GenealogyTheme(
            id = "moses_aaron",
            titleRu = "Левиты: Моисей и Аарон",
            descriptionRu = "От Иакова через Левия к Амраму.",
            startPersonId = "levi",
            highlightIds = listOf("levi", "kohath", "amram", "moses", "aaron"),
        ),
    )
}

data class GenealogyTheme(
    val id: String,
    val titleRu: String,
    val descriptionRu: String,
    val startPersonId: String,
    val highlightIds: List<String>,
)
