package com.example.bible.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.bible.data.BibleCanon
import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings

internal object BibleFtsSearch {

    /** FTS5 phrase: смежные токены как в [com.example.bible.data.verseSearchNormForStored]. */
    fun phraseMatch(normalizedQuery: String): String? {
        val t = normalizedQuery.trim()
        if (t.isEmpty()) return null
        val escaped = t.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun buildFtsQuery(
        translationCodes: List<String>,
        settings: SearchSettings,
        limit: Int,
        matchPhrase: String,
    ): SupportSQLiteQuery? {
        if (translationCodes.isEmpty()) return null
        if (settings.scope == SearchScope.SINGLE_BOOK && settings.singleBookId == null) return null
        val sql = StringBuilder(
            "SELECT v.translationCode, v.bookId, v.chapterNumber, v.verseNumber, v.text " +
                "FROM bible_verses_fts fts INNER JOIN bible_verses v ON " +
                "v.translationCode = fts.translationCode AND v.bookId = fts.bookId AND " +
                "v.chapterNumber = fts.chapterNumber AND v.verseNumber = fts.verseNumber " +
                "WHERE fts MATCH ? AND v.translationCode IN (",
        )
        val bind = ArrayList<Any>(32)
        bind.add(matchPhrase)
        sql.append(translationCodes.joinToString(",") { "?" })
        sql.append(") ")
        translationCodes.forEach { bind.add(it) }

        when (settings.scope) {
            SearchScope.ALL -> { }
            SearchScope.OLD_TESTAMENT -> {
                val ids = BibleCanon.oldTestamentIds.toList()
                sql.append("AND v.bookId IN (")
                sql.append(ids.joinToString(",") { "?" })
                sql.append(") ")
                ids.forEach { bind.add(it) }
            }
            SearchScope.NEW_TESTAMENT -> {
                val ids = BibleCanon.newTestamentIds.toList()
                sql.append("AND v.bookId IN (")
                sql.append(ids.joinToString(",") { "?" })
                sql.append(") ")
                ids.forEach { bind.add(it) }
            }
            SearchScope.SINGLE_BOOK -> {
                sql.append("AND v.bookId = ? ")
                bind.add(settings.singleBookId!!)
            }
        }
        sql.append("ORDER BY bm25(fts) LIMIT ?")
        bind.add(limit)
        return SimpleSQLiteQuery(sql.toString(), bind.toTypedArray())
    }
}
