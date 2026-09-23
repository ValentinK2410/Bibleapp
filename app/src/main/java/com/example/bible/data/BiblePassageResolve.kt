package com.example.bible.data

/**
 * Разбор ввода вида «Ин 3:16», «Быт 1:1», «Пс 23:1» для быстрого перехода.
 */
object BiblePassageResolve {

    data class Resolved(
        val bookId: String,
        val chapter: Int,
        val verse: Int,
        val label: String,
    )

    fun resolve(raw: String): Resolved? {
        val text = raw.trim().replace('\u00A0', ' ')
        if (text.isEmpty()) return null
        val booksByPrefix = BibleCanon.allBooks
            .flatMap { book ->
                listOf(book.abbrRu, book.nameRu, book.nameEn, book.id.replace('_', ' '))
                    .map { prefix -> prefix.trim() to book }
            }
            .filter { it.first.isNotEmpty() }
            .sortedByDescending { it.first.length }

        for ((prefix, book) in booksByPrefix) {
            if (!text.startsWith(prefix, ignoreCase = true)) continue
            var rest = text.substring(prefix.length).trimStart()
            if (rest.startsWith('.')) rest = rest.drop(1).trimStart()
            val m = Regex("""^(\d+)\s*[:.,]\s*(\d+)""").find(rest)
                ?: Regex("""^(\d+)\s+(\d+)""").find(rest)
            if (m != null) {
                val ch = m.groupValues[1].toIntOrNull() ?: continue
                val vs = m.groupValues[2].toIntOrNull() ?: continue
                if (ch !in 1..book.chapters) continue
                if (vs < 1) continue
                return Resolved(
                    bookId = book.id,
                    chapter = ch,
                    verse = vs,
                    label = "${book.abbrRu} $ch:$vs",
                )
            }
            val chOnly = Regex("""^(\d+)""").find(rest)?.groupValues?.get(1)?.toIntOrNull()
            if (chOnly != null && chOnly in 1..book.chapters) {
                return Resolved(book.id, chOnly, 1, "${book.abbrRu} $chOnly:1")
            }
        }
        return null
    }
}
