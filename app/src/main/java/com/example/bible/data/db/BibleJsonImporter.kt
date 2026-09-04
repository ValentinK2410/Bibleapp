package com.example.bible.data.db

import android.content.Context
import com.example.bible.data.TranslationId
import com.example.bible.data.verseSearchNormForStored
import org.json.JSONArray
import org.json.JSONObject

/**
 * Первичное заполнение [BibleDatabase] из тех же JSON в [android.content.res.AssetManager], что и в проекте на JSON.
 */
object BibleJsonImporter {
    private val importLock = Any()

    /** Увеличивать при смене JSON в assets/bible/NRT/ — перезапишет НРП в Room у установленных пользователей. */
    private const val NRT_ASSET_REVISION = 2

    fun importFromAssetsIfNeeded(context: Context, database: BibleDatabase) {
        synchronized(importLock) {
            val dao = database.bibleDao()
            if (dao.countAllVerses() > 0) {
                reimportNrtIfNeeded(context, database)
                return
            }
            database.runInTransaction {
                if (dao.countAllVerses() > 0) return@runInTransaction
                importAllFromAssets(context, dao)
            }
        }
    }

    private fun importAllFromAssets(context: Context, dao: BibleDao) {
        val hadPerBook = TranslationId.entries.any { hasTranslationFolder(context, it) }
        if (hadPerBook) {
            for (t in TranslationId.entries) {
                importTranslationFromAssets(context, dao, t)
            }
        } else {
            importLegacyIfPresent(context, TranslationId.WEB, "bible_web_sample.json", dao)
            importLegacyIfPresent(context, TranslationId.SYNODAL, "bible_synodal_sample.json", dao)
        }
        markNrtAssetRevisionApplied(context)
    }

    private fun reimportNrtIfNeeded(context: Context, database: BibleDatabase) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_NRT_REVISION, 0) >= NRT_ASSET_REVISION) return
        if (!hasTranslationFolder(context, TranslationId.NRT)) return
        database.runInTransaction {
            val dao = database.bibleDao()
            dao.deleteTranslation(TranslationId.NRT.code)
            importTranslationFromAssets(context, dao, TranslationId.NRT)
        }
        markNrtAssetRevisionApplied(context)
    }

    private fun markNrtAssetRevisionApplied(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NRT_REVISION, NRT_ASSET_REVISION)
            .apply()
    }

    private fun importTranslationFromAssets(context: Context, dao: BibleDao, translation: TranslationId) {
        val ids = listBookFileIds(context, translation) ?: return
        for (bookId in ids) {
            val path = "bible/${translation.assetsFolder}/$bookId.json"
            val json = readAssetString(context, path)
            val parsed = parseSingleBookToRows(translation, json) ?: continue
            dao.replaceBook(parsed.book, parsed.verses, parsed.interlinear)
        }
    }

    private fun importLegacyIfPresent(
        context: Context,
        translation: TranslationId,
        assetName: String,
        dao: BibleDao,
    ) {
        if (hasTranslationFolder(context, translation)) return
        val data = readAssetString(context, assetName)
        val books = parseLegacyWholeToBooks(translation, data) ?: return
        for (b in books) {
            dao.replaceBook(b.book, b.verses, b.interlinear)
        }
    }

    private data class BookRows(
        val book: BibleBookEntity,
        val verses: List<BibleVerseEntity>,
        val interlinear: List<BibleInterlinearWordEntity>,
    )

    private fun parseSingleBookToRows(translation: TranslationId, json: String): BookRows? {
        return runCatching {
            val code = translation.code
            val root = JSONObject(json)
            val b = root.getJSONObject("book")
            val bookId = b.getString("id")
            val name = b.getString("name")
            val book = BibleBookEntity(code, bookId, name)
            val chaptersArray = b.getJSONArray("chapters")
            val verses = ArrayList<BibleVerseEntity>(512)
            val interlinear = ArrayList<BibleInterlinearWordEntity>(256)
            for (c in 0 until chaptersArray.length()) {
                val ch = chaptersArray.getJSONObject(c)
                val chNum = ch.getInt("number")
                val versesArray = ch.getJSONArray("verses")
                for (v in 0 until versesArray.length()) {
                    val verse = versesArray.getJSONObject(v)
                    val vNum = verse.getInt("number")
                    val text = verse.getString("text")
                    val imageUrl = if (verse.has("imageUrl")) verse.getString("imageUrl") else null
                    verses.add(
                        BibleVerseEntity(
                            code, bookId, chNum, vNum, text, imageUrl,
                            searchNorm = verseSearchNormForStored(text),
                        ),
                    )
                    val words = verse.optJSONArray("words")
                    if (words != null && words.length() > 0) {
                        for (i in 0 until words.length()) {
                            val w = words.getJSONObject(i)
                            interlinear.add(
                                BibleInterlinearWordEntity(
                                    translationCode = code,
                                    bookId = bookId,
                                    chapterNumber = chNum,
                                    verseNumber = vNum,
                                    wordIndex = i,
                                    original = w.getString("o"),
                                    transliteration = w.getString("t"),
                                    gloss = w.getString("r"),
                                    strong = if (w.has("s")) w.getString("s") else null,
                                    morph = if (w.has("m")) w.getString("m") else null,
                                ),
                            )
                        }
                    }
                }
            }
            BookRows(book, verses, interlinear)
        }.getOrNull()
    }

    private fun parseLegacyWholeToBooks(translation: TranslationId, json: String): List<BookRows>? {
        return runCatching {
            val code = translation.code
            val root = JSONObject(json)
            val booksArray = root.getJSONArray("books")
            val out = ArrayList<BookRows>(booksArray.length())
            for (i in 0 until booksArray.length()) {
                val b = booksArray.getJSONObject(i)
                val bookId = b.getString("id")
                val name = b.getString("name")
                val book = BibleBookEntity(code, bookId, name)
                val chaptersArray = b.getJSONArray("chapters")
                val verses = ArrayList<BibleVerseEntity>(256)
                for (c in 0 until chaptersArray.length()) {
                    val ch = chaptersArray.getJSONObject(c)
                    val chNum = ch.getInt("number")
                    val versesArray = ch.getJSONArray("verses")
                    for (v in 0 until versesArray.length()) {
                        val verse = versesArray.getJSONObject(v)
                        val vNum = verse.getInt("number")
                        val text = verse.getString("text")
                        val imageUrl = if (verse.has("imageUrl")) verse.getString("imageUrl") else null
                        verses.add(
                            BibleVerseEntity(
                                code, bookId, chNum, vNum, text, imageUrl,
                                searchNorm = verseSearchNormForStored(text),
                            ),
                        )
                    }
                }
                out.add(BookRows(book, verses, interlinear = emptyList()))
            }
            out
        }.getOrNull()
    }

    private fun hasTranslationFolder(context: Context, translation: TranslationId): Boolean =
        try {
            context.assets.list("bible/${translation.assetsFolder}")?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }

    private fun listBookFileIds(context: Context, translation: TranslationId): List<String>? {
        val folder = "bible/${translation.assetsFolder}"
        return try {
            context.assets.list(folder)
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readAssetString(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    private const val PREFS_NAME = "bible_json_import"
    private const val KEY_NRT_REVISION = "nrt_asset_revision"
}
