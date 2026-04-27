package com.example.bible.data

/**
 * Полный канон (66 книг) в порядке сетки как в эталонном UI:
 * ВЗ по порядку, затем НЗ: Евангелия → Деяния → Соборные послания → Послания Павла → Евреям → Откровение.
 */
enum class CanonBookGroup {
    PENTATEUCH,
    HISTORY,
    WISDOM,
    MAJOR_PROPHETS,
    MINOR_PROPHETS,
    GOSPELS,
    ACTS,
    GENERAL_EPISTLES,
    PAULINE,
    HEBREWS,
    REVELATION,
}

data class CanonBookEntry(
    val id: String,
    val abbrRu: String,
    val nameRu: String,
    val nameEn: String,
    val group: CanonBookGroup,
    val chapters: Int = 1,
)

object BibleCanon {
    val allBooks: List<CanonBookEntry> = buildList {
        // Ветхий завет
        add(CanonBookEntry("genesis", "Быт", "Бытие", "Genesis", CanonBookGroup.PENTATEUCH, 50))
        add(CanonBookEntry("exodus", "Исх", "Исход", "Exodus", CanonBookGroup.PENTATEUCH, 40))
        add(CanonBookEntry("leviticus", "Лев", "Левит", "Leviticus", CanonBookGroup.PENTATEUCH, 27))
        add(CanonBookEntry("numbers", "Чис", "Числа", "Numbers", CanonBookGroup.PENTATEUCH, 36))
        add(CanonBookEntry("deuteronomy", "Втор", "Второзаконие", "Deuteronomy", CanonBookGroup.PENTATEUCH, 34))
        add(CanonBookEntry("joshua", "Нав", "Иисус Навин", "Joshua", CanonBookGroup.HISTORY, 24))
        add(CanonBookEntry("judges", "Суд", "Судьи", "Judges", CanonBookGroup.HISTORY, 21))
        add(CanonBookEntry("ruth", "Руфь", "Руфь", "Ruth", CanonBookGroup.HISTORY, 4))
        add(CanonBookEntry("1_samuel", "1Цар", "1-я Царств", "1 Samuel", CanonBookGroup.HISTORY, 31))
        add(CanonBookEntry("2_samuel", "2Цар", "2-я Царств", "2 Samuel", CanonBookGroup.HISTORY, 24))
        add(CanonBookEntry("1_kings", "3Цар", "3-я Царств", "1 Kings", CanonBookGroup.HISTORY, 22))
        add(CanonBookEntry("2_kings", "4Цар", "4-я Царств", "2 Kings", CanonBookGroup.HISTORY, 25))
        add(CanonBookEntry("1_chronicles", "1Пар", "1-я Паралипоменон", "1 Chronicles", CanonBookGroup.HISTORY, 29))
        add(CanonBookEntry("2_chronicles", "2Пар", "2-я Паралипоменон", "2 Chronicles", CanonBookGroup.HISTORY, 36))
        add(CanonBookEntry("ezra", "Ездр", "Ездра", "Ezra", CanonBookGroup.HISTORY, 10))
        add(CanonBookEntry("nehemiah", "Неем", "Неемия", "Nehemiah", CanonBookGroup.HISTORY, 13))
        add(CanonBookEntry("esther", "Есф", "Есфирь", "Esther", CanonBookGroup.HISTORY, 10))
        add(CanonBookEntry("job", "Иов", "Иов", "Job", CanonBookGroup.WISDOM, 42))
        add(CanonBookEntry("psalms", "Пс", "Псалтирь", "Psalms", CanonBookGroup.WISDOM, 150))
        add(CanonBookEntry("proverbs", "Прит", "Притчи", "Proverbs", CanonBookGroup.WISDOM, 31))
        add(CanonBookEntry("ecclesiastes", "Еккл", "Екклесиаст", "Ecclesiastes", CanonBookGroup.WISDOM, 12))
        add(CanonBookEntry("song_of_solomon", "Песн", "Песнь Песней", "Song of Solomon", CanonBookGroup.WISDOM, 8))
        add(CanonBookEntry("isaiah", "Ис", "Исаия", "Isaiah", CanonBookGroup.MAJOR_PROPHETS, 66))
        add(CanonBookEntry("jeremiah", "Иер", "Иеремия", "Jeremiah", CanonBookGroup.MAJOR_PROPHETS, 52))
        add(CanonBookEntry("lamentations", "Плач", "Плач Иеремии", "Lamentations", CanonBookGroup.MAJOR_PROPHETS, 5))
        add(CanonBookEntry("ezekiel", "Иез", "Иезекииль", "Ezekiel", CanonBookGroup.MAJOR_PROPHETS, 48))
        add(CanonBookEntry("daniel", "Дан", "Даниил", "Daniel", CanonBookGroup.MAJOR_PROPHETS, 12))
        add(CanonBookEntry("hosea", "Ос", "Осия", "Hosea", CanonBookGroup.MINOR_PROPHETS, 14))
        add(CanonBookEntry("joel", "Иоил", "Иоиль", "Joel", CanonBookGroup.MINOR_PROPHETS, 3))
        add(CanonBookEntry("amos", "Ам", "Амос", "Amos", CanonBookGroup.MINOR_PROPHETS, 9))
        add(CanonBookEntry("obadiah", "Авд", "Авдий", "Obadiah", CanonBookGroup.MINOR_PROPHETS, 1))
        add(CanonBookEntry("jonah", "Ион", "Иона", "Jonah", CanonBookGroup.MINOR_PROPHETS, 4))
        add(CanonBookEntry("micah", "Мих", "Михей", "Micah", CanonBookGroup.MINOR_PROPHETS, 7))
        add(CanonBookEntry("nahum", "Наум", "Наум", "Nahum", CanonBookGroup.MINOR_PROPHETS, 3))
        add(CanonBookEntry("habakkuk", "Авв", "Аввакум", "Habakkuk", CanonBookGroup.MINOR_PROPHETS, 3))
        add(CanonBookEntry("zephaniah", "Соф", "Софония", "Zephaniah", CanonBookGroup.MINOR_PROPHETS, 3))
        add(CanonBookEntry("haggai", "Агг", "Аггей", "Haggai", CanonBookGroup.MINOR_PROPHETS, 2))
        add(CanonBookEntry("zechariah", "Зах", "Захария", "Zechariah", CanonBookGroup.MINOR_PROPHETS, 14))
        add(CanonBookEntry("malachi", "Мал", "Малахия", "Malachi", CanonBookGroup.MINOR_PROPHETS, 4))
        // Новый завет
        add(CanonBookEntry("matthew", "Мат", "От Матфея", "Matthew", CanonBookGroup.GOSPELS, 28))
        add(CanonBookEntry("mark", "Мар", "От Марка", "Mark", CanonBookGroup.GOSPELS, 16))
        add(CanonBookEntry("luke", "Лук", "От Луки", "Luke", CanonBookGroup.GOSPELS, 24))
        add(CanonBookEntry("john", "Ин", "От Иоанна", "John", CanonBookGroup.GOSPELS, 21))
        add(CanonBookEntry("acts", "Деян", "Деяния", "Acts", CanonBookGroup.ACTS, 28))
        add(CanonBookEntry("james", "Иак", "Иакова", "James", CanonBookGroup.GENERAL_EPISTLES, 5))
        add(CanonBookEntry("1_peter", "1Пет", "1-е Петра", "1 Peter", CanonBookGroup.GENERAL_EPISTLES, 5))
        add(CanonBookEntry("2_peter", "2Пет", "2-е Петра", "2 Peter", CanonBookGroup.GENERAL_EPISTLES, 3))
        add(CanonBookEntry("1_john", "1Ин", "1-е Иоанна", "1 John", CanonBookGroup.GENERAL_EPISTLES, 5))
        add(CanonBookEntry("2_john", "2Ин", "2-е Иоанна", "2 John", CanonBookGroup.GENERAL_EPISTLES, 1))
        add(CanonBookEntry("3_john", "3Ин", "3-е Иоанна", "3 John", CanonBookGroup.GENERAL_EPISTLES, 1))
        add(CanonBookEntry("jude", "Иуд", "Иуды", "Jude", CanonBookGroup.GENERAL_EPISTLES, 1))
        add(CanonBookEntry("romans", "Рим", "К Римлянам", "Romans", CanonBookGroup.PAULINE, 16))
        add(CanonBookEntry("1_corinthians", "1Кор", "1-е Коринфянам", "1 Corinthians", CanonBookGroup.PAULINE, 16))
        add(CanonBookEntry("2_corinthians", "2Кор", "2-е Коринфянам", "2 Corinthians", CanonBookGroup.PAULINE, 13))
        add(CanonBookEntry("galatians", "Гал", "К Галатам", "Galatians", CanonBookGroup.PAULINE, 6))
        add(CanonBookEntry("ephesians", "Еф", "К Ефесянам", "Ephesians", CanonBookGroup.PAULINE, 6))
        add(CanonBookEntry("philippians", "Флп", "К Филиппийцам", "Philippians", CanonBookGroup.PAULINE, 4))
        add(CanonBookEntry("colossians", "Кол", "К Колоссянам", "Colossians", CanonBookGroup.PAULINE, 4))
        add(CanonBookEntry("1_thessalonians", "1Фес", "1-е Фессалоникийцам", "1 Thessalonians", CanonBookGroup.PAULINE, 5))
        add(CanonBookEntry("2_thessalonians", "2Фес", "2-е Фессалоникийцам", "2 Thessalonians", CanonBookGroup.PAULINE, 3))
        add(CanonBookEntry("1_timothy", "1Тим", "1-е Тимофею", "1 Timothy", CanonBookGroup.PAULINE, 6))
        add(CanonBookEntry("2_timothy", "2Тим", "2-е Тимофею", "2 Timothy", CanonBookGroup.PAULINE, 4))
        add(CanonBookEntry("titus", "Тит", "Титу", "Titus", CanonBookGroup.PAULINE, 3))
        add(CanonBookEntry("philemon", "Флм", "К Филимону", "Philemon", CanonBookGroup.PAULINE, 1))
        add(CanonBookEntry("hebrews", "Евр", "К Евреям", "Hebrews", CanonBookGroup.HEBREWS, 13))
        add(CanonBookEntry("revelation", "Откр", "Откровение", "Revelation", CanonBookGroup.REVELATION, 22))
    }

    fun byId(id: String): CanonBookEntry? = allBooks.find { it.id == id }

    fun displayName(entry: CanonBookEntry, translation: TranslationId): String =
        when (translation) {
            TranslationId.WEB -> entry.nameEn
            else -> entry.nameRu
        }

    private val otGroups = setOf(
        CanonBookGroup.PENTATEUCH,
        CanonBookGroup.HISTORY,
        CanonBookGroup.WISDOM,
        CanonBookGroup.MAJOR_PROPHETS,
        CanonBookGroup.MINOR_PROPHETS,
    )

    val oldTestamentIds: Set<String> by lazy {
        allBooks.filter { it.group in otGroups }.map { it.id }.toSet()
    }

    val newTestamentIds: Set<String> by lazy {
        allBooks.filter { it.group !in otGroups }.map { it.id }.toSet()
    }

    fun isOldTestament(bookId: String): Boolean = bookId in oldTestamentIds
    fun isNewTestament(bookId: String): Boolean = bookId in newTestamentIds
}
