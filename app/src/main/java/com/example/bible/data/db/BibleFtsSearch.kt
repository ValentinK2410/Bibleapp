package com.example.bible.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.bible.data.BibleCanon
import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings

/**
 * Один SQL-запрос по [BibleVerseEntity.searchNorm] с LIKE (без FTS5 — совместимо со всеми сборками SQLite).
 */
internal object BibleNormLikeSearch {

    fun likePatternForNormalizedQuery(normalizedQuery: String): String? {
        val t = normalizedQuery.trim()
        if (t.isEmpty()) return null
        val escaped = t
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    fun buildLikeQuery(
        translationCodes: List<String>,
        settings: SearchSettings,
        limit: Int,
        likePattern: String,
    ): SupportSQLiteQuery? {
        if (translationCodes.isEmpty()) return null
        if (settings.scope == SearchScope.SINGLE_BOOK && settings.singleBookId == null) return null
        val sql = StringBuilder(
            "SELECT v.translationCode, v.bookId, v.chapterNumber, v.verseNumber, v.text " +
                "FROM bible_verses v WHERE v.translationCode IN (",
        )
        val bind = ArrayList<Any>(48)
        sql.append(translationCodes.joinToString(",") { "?" })
        sql.append(") AND v.searchNorm LIKE ? ESCAPE '\\' ")
        translationCodes.forEach { bind.add(it) }
        bind.add(likePattern)

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
        sql.append(
            "ORDER BY v.translationCode, v.bookId, v.chapterNumber, v.verseNumber LIMIT ?",
        )
        bind.add(limit)
        return SimpleSQLiteQuery(sql.toString(), bind.toTypedArray())
    }
}
