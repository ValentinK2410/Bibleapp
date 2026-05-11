package com.example.bible.data.languagestudy

import android.content.Context
import android.net.Uri
import com.example.bible.data.db.LangVocabWordEntity
import com.example.bible.data.db.StudyDatabase
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

/**
 * Импорт пакета слов: ZIP с `pack.json` и `words.jsonl`,
 * либо строки JSONL без манифеста (демо из assets без zip).
 */
class LangPackImporter(private val context: Context) {

    private val dao get() = StudyDatabase.getInstance(context).studyDao()

    companion object {
        fun wordKey(lang: String, stableId: String): String = "$lang|$stableId"
    }

    /**
     * Импорт из URI ( контент-документ, файл из загрузок).
     * @return число импортированных слов или результат ошибки
     */
    fun importZipFromUri(uri: Uri): Result<Int> =
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    val entries = readZipFlat(zis)
                    val manifestBytes = entries["pack.json"]
                        ?: entries.entries.firstOrNull { it.key.equals("pack.json", true) }?.value
                        ?: return Result.failure(IllegalArgumentException("В архиве нет pack.json"))
                    val wordsBytes = entries["words.jsonl"]
                        ?: entries.entries.firstOrNull { it.key.endsWith("words.jsonl", true) }?.value
                        ?: return Result.failure(IllegalArgumentException("Нет words.jsonl"))
                    val manifest = LangPackManifest.parse(JSONObject(manifestBytes.decodeToString()))
                    val wordsBuffer = mutableListOf<LangVocabWordEntity>()
                    parseJsonlWords(wordsBytes.decodeToString(), manifest, wordsBuffer)
                    if (wordsBuffer.isEmpty()) {
                        return Result.failure(IllegalArgumentException("words.jsonl не содержит слов"))
                    }
                    persistReplaceLanguage(manifest.lang, wordsBuffer)
                    Result.success(wordsBuffer.size)
                }
            } ?: Result.failure(IllegalArgumentException("Не удалось открыть файл"))
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun readZipFlat(zis: ZipInputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name.substringAfterLast('/')
                map[name] = zis.readBytes()
            }
            entry = zis.nextEntry
        }
        return map
    }

    /** Поток уже как JSON манифест + тело JSONL отдельно (для тестов). */
    fun importFromStrings(lang: String, packVersion: String, jsonl: String): Result<Int> {
        val manifest = LangPackManifest(lang = lang, packVersion = packVersion, schema = 1)
        val wordsBuffer = mutableListOf<LangVocabWordEntity>()
        parseJsonlWords(jsonl, manifest, wordsBuffer)
        if (wordsBuffer.isEmpty()) return Result.failure(IllegalArgumentException("Пустой список"))
        persistReplaceLanguage(lang, wordsBuffer)
        return Result.success(wordsBuffer.size)
    }

    /** Встроенный zip из assets, путь без ведущего слэша. */
    fun importZipFromStream(stream: java.io.InputStream): Result<Int> =
        try {
            ZipInputStream(BufferedInputStream(stream)).use { zis ->
                val entries = readZipFlat(zis)
                val manifestBytes = entries["pack.json"]
                    ?: return Result.failure(IllegalArgumentException("Нет pack.json"))
                val wordsBytes = entries["words.jsonl"]
                    ?: return Result.failure(IllegalArgumentException("Нет words.jsonl"))
                val manifest = LangPackManifest.parse(JSONObject(manifestBytes.decodeToString()))
                val wordsBuffer = mutableListOf<LangVocabWordEntity>()
                parseJsonlWords(wordsBytes.decodeToString(), manifest, wordsBuffer)
                if (wordsBuffer.isEmpty()) {
                    return Result.failure(IllegalArgumentException("words.jsonl пуст"))
                }
                persistReplaceLanguage(manifest.lang, wordsBuffer)
                Result.success(wordsBuffer.size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun importBundledAssetZip(assetPath: String): Result<Int> =
        try {
            context.assets.open(assetPath).use { importZipFromStream(it) }
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun parseJsonlWords(
        text: String,
        manifest: LangPackManifest?,
        out: MutableList<LangVocabWordEntity>,
    ) {
        val lang = manifest?.lang ?: return
        val ver = manifest.packVersion
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val jo = try {
                    JSONObject(line)
                } catch (_: Exception) {
                    return@forEach
                }
                val (id, fields) = jo.toLangPackWordDto()
                if (id != null && fields != null) {
                    out.add(fields.toWordEntity(lang, id, ver))
                }
            }
    }

    private fun persistReplaceLanguage(lang: String, words: List<LangVocabWordEntity>) {
        val db = StudyDatabase.getInstance(context)
        db.runInTransaction {
            dao.deleteLangWordsForLanguage(lang)
            val batch = 400
            words.chunked(batch).forEach { chunk -> dao.upsertLangVocab(chunk) }
        }
    }
}
