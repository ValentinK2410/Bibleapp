package com.example.bible.data.languagestudy

import android.content.Context
import android.net.Uri
import com.example.bible.data.db.LangVocabWordEntity
import com.example.bible.data.db.StudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class LanguageStudyRepository(context: Context) {
    private val app = context.applicationContext
    private val dao get() = StudyDatabase.getInstance(app).studyDao()
    private val importer = LangPackImporter(app)
    private val bundledPrefs = app.getSharedPreferences("language_study_bundled", Context.MODE_PRIVATE)

    companion object {
        private const val DEMO_VERSION = "demo-1"
        private const val DEMO_ASSET_ZIP = "language_packs/bundled/"
        /** Ниже порога считаем, что нужен импорт полного архива из assets (если он есть). */
        private const val FULL_PACK_IMPORT_THRESHOLD = 900

        private fun bundledPackVerKey(langCode: String) = "bundled_pack_ver_$langCode"
    }

    private fun bundledZipFileName(langCode: String) = "${langCode}_v1.zip"

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

    private fun bundledPackAssetExists(langCode: String): Boolean =
        try {
            app.assets.open(DEMO_ASSET_ZIP + bundledZipFileName(langCode)).close()
            true
        } catch (_: IOException) {
            false
        }

    private fun readBundledManifestVersion(assetFileName: String): String? =
        try {
            app.assets.open(DEMO_ASSET_ZIP + assetFileName).use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val shortName = entry.name.substringAfterLast('/')
                        if (shortName.equals("pack.json", ignoreCase = true)) {
                            val json = JSONObject(zis.readBytes().decodeToString())
                            return json.optString("version").trim().ifBlank { null }
                        }
                        entry = zis.nextEntry
                    }
                    null
                }
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Полный офлайн-пакет из assets заменяет демо, если строк меньше [FULL_PACK_IMPORT_THRESHOLD],
     * либо если в архиве новая [version] в pack.json (обновление переводов без сброса данных вручную).
     */
    fun ensureBundledFullOrDemo(langCode: String) {
        val haveBundled = bundledPackAssetExists(langCode)
        if (!haveBundled) {
            if (dao.countLangWords(langCode) == 0) {
                val jsonl = LanguageStudyDemoJsonl.buildJsonlForLang(langCode)
                importer.importFromStrings(langCode, DEMO_VERSION, jsonl)
            }
            return
        }
        val zipName = bundledZipFileName(langCode)
        val bundledVer = readBundledManifestVersion(zipName) ?: return
        val count = dao.countLangWords(langCode)
        val prevVer = bundledPrefs.getString(bundledPackVerKey(langCode), null).orEmpty()
        val needImport =
            count < FULL_PACK_IMPORT_THRESHOLD ||
                prevVer.isBlank() ||
                prevVer != bundledVer
        if (!needImport) return
        importBundledFullPack(zipName).onSuccess {
            bundledPrefs.edit().putString(bundledPackVerKey(langCode), bundledVer).apply()
        }
    }

    /** Только демо, если язык совсем без записей (для особых сборок без zip). */
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