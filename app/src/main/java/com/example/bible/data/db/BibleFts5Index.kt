package com.example.bible.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.bible.data.BibleCanon
import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings

/**
 * Опциональный FTS5-индекс для быстрого поиска по [BibleVerseEntity.searchNorm].
 * На устройствах без FTS5 остаётся LIKE-путь в [BibleNormLikeSearch].
 */
internal object BibleFts5Index {

    private const val TABLE = "bible_verses_fts"
    private const val FTS_BUILD_BATCH = 4000

    private var availableCache: Boolean? = null

    @Volatile
    private var indexReady = false

    fun isFts5Available(db: BibleDatabase): Boolean {
        availableCache?.let { return it }
        val ok = runCatching {
            db.openHelper.writableDatabase.use { wdb ->
                wdb.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS _bible_fts5_probe USING fts5(x)")
                wdb.execSQL("DROP TABLE IF EXISTS _bible_fts5_probe")
            }
            true
        }.getOrDefault(false)
        availableCache = ok
        return ok
    }

    /** true только когда FTS полностью построен (не проверяем COUNT на горячем пути). */
    fun isIndexReady(db: BibleDatabase): Boolean {
        if (indexReady) return true
        if (!isFts5Available(db)) return false
        return false
    }

    /**
     * Один пакет построения FTS. @return true если нужны ещё пакеты.
     */
    fun ensureBuiltBatch(db: BibleDatabase): Boolean {
        if (!isFts5Available(db)) return false
        if (indexReady) return false

        db.openHelper.writableDatabase.use { wdb ->
            ensureTableAndTriggers(wdb)
            val inserted = insertNextBatch(wdb)
            if (inserted == 0) {
                indexReady = true
                return false
            }
        }
        return true
    }

    private fun ensureTableAndTriggers(wdb: androidx.sqlite.db.SupportSQLiteDatabase) {
        wdb.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(
              searchNorm,
              translationCode UNINDEXED,
              bookId UNINDEXED,
              chapterNumber UNINDEXED,
              verseNumber UNINDEXED,
              text UNINDEXED,
              tokenize='unicode61 remove_diacritics 2'
            )
            """.trimIndent(),
        )
        createTriggersIfNeeded(wdb)
    }

    private fun insertNextBatch(wdb: androidx.sqlite.db.SupportSQLiteDatabase): Int {
        wdb.execSQL(
            """
            INSERT INTO $TABLE(searchNorm, translationCode, bookId, chapterNumber, verseNumber, text)
            SELECT v.searchNorm, v.translationCode, v.bookId, v.chapterNumber, v.verseNumber, v.text
            FROM bible_verses v
            WHERE v.searchNorm != ''
              AND NOT EXISTS (
                SELECT 1 FROM $TABLE f
                WHERE f.translationCode = v.translationCode
                  AND f.bookId = v.bookId
                  AND f.chapterNumber = v.chapterNumber
                  AND f.verseNumber = v.verseNumber
              )
            ORDER BY v.translationCode, v.bookId, v.chapterNumber, v.verseNumber
            LIMIT $FTS_BUILD_BATCH
            """.trimIndent(),
        )
        return wdb.query("SELECT changes()").use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    private fun createTriggersIfNeeded(wdb: androidx.sqlite.db.SupportSQLiteDatabase) {
        wdb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS bible_verses_fts_ai AFTER INSERT ON bible_verses
            WHEN NEW.searchNorm != ''
            BEGIN
              INSERT INTO $TABLE(searchNorm, translationCode, bookId, chapterNumber, verseNumber, text)
              VALUES (NEW.searchNorm, NEW.translationCode, NEW.bookId, NEW.chapterNumber, NEW.verseNumber, NEW.text);
            END
            """.trimIndent(),
        )
        wdb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS bible_verses_fts_ad AFTER DELETE ON bible_verses BEGIN
              DELETE FROM $TABLE
              WHERE translationCode = OLD.translationCode
                AND bookId = OLD.bookId
                AND chapterNumber = OLD.chapterNumber
                AND verseNumber = OLD.verseNumber;
            END
            """.trimIndent(),
        )
        wdb.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS bible_verses_fts_au AFTER UPDATE ON bible_verses BEGIN
              DELETE FROM $TABLE
              WHERE translationCode = OLD.translationCode
                AND bookId = OLD.bookId
                AND chapterNumber = OLD.chapterNumber
                AND verseNumber = OLD.verseNumber;
              INSERT INTO $TABLE(searchNorm, translationCode, bookId, chapterNumber, verseNumber, text)
              SELECT NEW.searchNorm, NEW.translationCode, NEW.bookId, NEW.chapterNumber, NEW.verseNumber, NEW.text
              WHERE NEW.searchNorm != '';
            END
            """.trimIndent(),
        )
    }

    /** FTS5 MATCH-выражение для нормализованного запроса (фраза или префикс слова). */
    fun matchExpressionForNormalizedQuery(normalizedQuery: String): String? {
        val tokens = normalizedQuery.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return if (tokens.size == 1) {
            val t = escapeFtsToken(tokens.single())
            if (t.isEmpty()) return null
            "searchNorm : $t*"
        } else {
            val phrase = tokens.mapNotNull { escapeFtsPhraseToken(it).takeIf { p -> p.isNotEmpty() } }
            if (phrase.isEmpty()) return null
            "\"${phrase.joinToString(" ")}\""
        }
    }

    private fun escapeFtsToken(token: String): String =
        token.replace(Regex("""["*:^\-()]"""), " ").trim().replace(Regex("\\s+"), " ")
            .replace("\"", "\"\"")

    private fun escapeFtsPhraseToken(token: String): String =
        token.replace(Regex("""["*:^\-()]"""), " ").trim().replace(Regex("\\s+"), " ")
            .replace("\"", "\"\"")

    fun buildSearchQuery(
        translationCodes: List<String>,
        settings: SearchSettings,
        limit: Int,
        matchExpression: String,
    ): SupportSQLiteQuery? {
        if (translationCodes.isEmpty()) return null
        if (settings.scope == SearchScope.SINGLE_BOOK && settings.singleBookId == null) return null
        val sql = StringBuilder(
            "SELECT translationCode, bookId, chapterNumber, verseNumber, text " +
                "FROM $TABLE WHERE $TABLE MATCH ? AND translationCode IN (",
        )
        val bind = ArrayList<Any>(48)
        bind.add(matchExpression)
        sql.append(translationCodes.joinToString(",") { "?" })
        sql.append(") ")
        translationCodes.forEach { bind.add(it) }

        when (settings.scope) {
            SearchScope.ALL -> { }
            SearchScope.OLD_TESTAMENT -> {
                val ids = BibleCanon.oldTestamentIds.toList()
                sql.append("AND bookId IN (")
                sql.append(ids.joinToString(",") { "?" })
                sql.append(") ")
                ids.forEach { bind.add(it) }
            }
            SearchScope.NEW_TESTAMENT -> {
                val ids = BibleCanon.newTestamentIds.toList()
                sql.append("AND bookId IN (")
                sql.append(ids.joinToString(",") { "?" })
                sql.append(") ")
                ids.forEach { bind.add(it) }
            }
            SearchScope.SINGLE_BOOK -> {
                sql.append("AND bookId = ? ")
                bind.add(settings.singleBookId!!)
            }
        }
        sql.append(
            "ORDER BY translationCode, bookId, chapterNumber, verseNumber LIMIT ?",
        )
        bind.add(limit)
        return SimpleSQLiteQuery(sql.toString(), bind.toTypedArray())
    }

    /** Однократная проверка при старте (в фоне): FTS уже был построен ранее. */
    fun markReadyIfPopulated(db: BibleDatabase) {
        if (indexReady || !isFts5Available(db)) return
        runCatching {
            db.openHelper.readableDatabase.use { rdb ->
                val hasTable = rdb.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(TABLE),
                ).use { it.moveToFirst() }
                if (!hasTable) return@runCatching
                val ftsCount = rdb.query("SELECT COUNT(*) FROM $TABLE").use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                val verseCount = rdb.query(
                    "SELECT COUNT(*) FROM bible_verses WHERE searchNorm != ''",
                ).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                if (verseCount > 0 && ftsCount == verseCount) {
                    indexReady = true
                }
            }
        }
    }

    fun invalidateCache() {
        indexReady = false
    }
}
