package com.example.bible.data.languagestudy

import android.content.Context
import android.net.Uri
import com.example.bible.data.db.LangVocabWordEntity
import com.example.bible.data.db.StudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class LanguageStudyRepository(context: Context) {
    private val app = context.applicationContext
    private val dao get() = StudyDatabase.getInstance(app).studyDao()
    private val importer = LangPackImporter(app)

    companion object {
        private const val DEMO_VERSION = "demo-1"
        private const val DEMO_ASSET_ZIP = "language_packs/bundled/"
    }

    fun countWords(langCode: String): Int = dao.countLangWords(langCode)

    fun dueCount(langCode: String, nowMs: Long = System.currentTimeMillis()): Int =
        dao.countDueLangWords(langCode, nowMs)

    fun listDueWords(langCode: String, limit: Int = 32, nowMs: Long = System.currentTimeMillis()): List<LangVocabWordEntity> =
        dao.getDueLangWords(langCode, nowMs, limit)

    fun getWord(wordKey: String): LangVocabWordEntity? = dao.getLangWord(wordKey)

    fun getSrs(wordKey: String) = dao.getLangSrsCard(wordKey)

    fun upsertSrs(card: com.example.bible.data.db.LangSrsCardEntity) {
        dao.upsertLangSrsCards(listOf(card))
    }

    fun updateUserNote(wordKey: String, note: String?) {
        val prev = dao.getLangSrsCard(wordKey)
        val trimmed = note?.trim()?.ifBlank { null }
        val next = if (prev != null) {
            prev.copy(userNote = trimmed)
        } else {
            com.example.bible.data.db.LangSrsCardEntity(
                wordKey = wordKey,
                easeFactor = 2.5,
                intervalDays = 0,
                repetitions = 0,
                nextReviewAtEpochMs = 0L,
                lastReviewAtEpochMs = null,
                userNote = trimmed,
            )
        }
        dao.upsertLangSrsCards(listOf(next))
    }

    fun searchWords(langCode: String, needle: String, limit: Int = 200): List<LangVocabWordEntity> =
        if (needle.isBlank()) emptyList()
        else dao.searchLangWords(langCode, needle.trim(), limit)

    /**
     * Если для языка ещё нет слов — подставляем демо-колоду (не трогает уже импортированные пакеты).
     */
    fun ensureDemoIfEmpty(langCode: String) {
        if (dao.countLangWords(langCode) > 0) return
        val jsonl = LanguageStudyDemoJsonl.buildJsonlForLang(langCode)
        importer.importFromStrings(langCode, DEMO_VERSION, jsonl)
    }

    fun importPackFromUri(uri: Uri): Result<Int> = importer.importZipFromUri(uri)

    fun importBundledFullPack(assetFileName: String): Result<Int> =
        importer.importBundledAssetZip(DEMO_ASSET_ZIP + assetFileName)

    suspend fun downloadAndImportPack(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 40_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                importer.importZipFromStream(BufferedInputStream(input))
            }.also { conn.disconnect() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}