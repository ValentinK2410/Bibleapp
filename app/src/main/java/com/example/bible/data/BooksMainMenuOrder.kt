package com.example.bible.data

/** Идентификаторы пунктов главного меню (экран книг), кроме фиксированных «Настройки» и блока переводов. */
object BooksMainMenuOrder {
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val NOTES = "notes"
    const val DUAL = "dual"
    const val MAPS = "maps"
    const val TRAVEL = "travel"
    const val GENEALOGY = "genealogy"
    const val OTHER_BOOKS = "other_books"
    const val STRONGS = "strongs"
    const val SEMANTIC_LEXICON = "semantic_lexicon"
    const val TIMEMARK = "timemark"
    const val MEDIA = "media"
    const val NARRATOR = "narrator"
    const val READING_PLAN = "reading_plan"
    const val NETWORK_REGION = "network_region"
    const val KIDS = "kids"
    const val EXPERIMENT = "experiment"
    const val TEXT_SIZE = "text_size"
    const val MY_CHURCH = "my_church"

    val allIds: List<String> = listOf(
        SEARCH,
        BOOKMARKS,
        HISTORY,
        NOTES,
        DUAL,
        MAPS,
        TRAVEL,
        GENEALOGY,
        OTHER_BOOKS,
        STRONGS,
        SEMANTIC_LEXICON,
        TIMEMARK,
        MEDIA,
        NARRATOR,
        READING_PLAN,
        NETWORK_REGION,
        KIDS,
        MY_CHURCH,
        EXPERIMENT,
        TEXT_SIZE,
    )

    fun defaultOrder(): List<String> = allIds.toList()

    fun normalize(ids: List<String>?): List<String> {
        if (ids.isNullOrEmpty()) return defaultOrder()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        ids.forEach { id ->
            if (id in allIds && id !in seen) {
                seen.add(id)
                out.add(id)
            }
        }
        allIds.forEach { id ->
            if (id !in seen) out.add(id)
        }
        return out
    }

    fun parseStored(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return defaultOrder()
        return normalize(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun toStored(ids: List<String>): String = normalize(ids).joinToString(",")

    fun titleRu(id: String): String = when (id) {
        SEARCH -> "Поиск"
        BOOKMARKS -> "Закладки"
        HISTORY -> "История чтения"
        NOTES -> "Заметки"
        DUAL -> "Сравнение переводов"
        MAPS -> "Карты"
        TRAVEL -> "Мои путешествия"
        GENEALOGY -> "Родословная"
        OTHER_BOOKS -> "Другие книги"
        STRONGS -> "Словарь Стронга"
        SEMANTIC_LEXICON -> "Словарь тематической подсветки"
        TIMEMARK -> "Редактор таймкодов"
        MEDIA -> "Медиа"
        NARRATOR -> "Озвучка главы"
        READING_PLAN -> "План чтения"
        NETWORK_REGION -> "Сеть и регион"
        KIDS -> "Детям"
        EXPERIMENT -> "Эксперимент"
        TEXT_SIZE -> "Размер текста"
        MY_CHURCH -> "Моя церковь"
        else -> id
    }
}
