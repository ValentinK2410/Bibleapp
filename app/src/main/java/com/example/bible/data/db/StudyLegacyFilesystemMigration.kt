package com.example.bible.data.db

import android.content.Context
import java.io.File

/**
 * Однократный перенос старого каталога [study_cache] в Room. После успешного переноса каталог удаляется.
 */
object StudyLegacyFilesystemMigration {

    private const val PREFS = "study_sqlite_migration"
    private const val KEY_DONE = "legacy_fs_import_done"

    fun runIfNeeded(context: Context, db: StudyDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        val root = File(context.filesDir, "study_cache")
        if (!root.isDirectory) {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return
        }
        val dao = db.studyDao()
        try {
            migrateChapterCommentaries(root, dao)
            migrateVerseJsonFolder(root, "verse_cmp", StudyVerseBlobKind.VERSE_COMPARISON, dao)
            migrateVerseJsonFolder(root, "cross", StudyVerseBlobKind.CROSS_REFERENCE, dao)
            migrateVerseJsonFolder(root, "strong", StudyVerseBlobKind.STRONG_WORDS, dao)
            migrateVerseCommentaryApi(root, dao)
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            root.deleteRecursively()
        } catch (_: Exception) {
            // оставляем файлы; повторим в следующий запуск
        }
    }

    private fun migrateChapterCommentaries(root: File, dao: StudyDao) {
        val dir = File(root, "commentary")
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { slugDir ->
            if (!slugDir.isDirectory) return@forEach
            val slug = slugDir.name
            slugDir.listFiles()?.forEach { f ->
                if (!f.isFile || !f.name.endsWith(".txt")) return@forEach
                val parsed = parseChapterFileBaseName(f.nameWithoutExtension) ?: return@forEach
                val text = try {
                    f.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    return@forEach
                }
                if (text.isBlank()) return@forEach
                dao.upsertChapterCommentary(
                    StudyChapterCommentaryEntity(
                        slug = slug,
                        bookId = parsed.first,
                        chapter = parsed.second,
                        text = text,
                    ),
                )
            }
        }
    }

    private fun migrateVerseJsonFolder(
        root: File,
        sub: String,
        kind: String,
        dao: StudyDao,
    ) {
        val dir = File(root, sub)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".json")) return@forEach
            val t = parseVerseTripletName(f.nameWithoutExtension) ?: return@forEach
            val payload = try {
                f.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                return@forEach
            }
            if (payload.isBlank()) return@forEach
            dao.upsertVerseBlob(
                StudyVerseBlobEntity(
                    kind = kind,
                    translationCode = "",
                    bookId = t.first,
                    chapter = t.second,
                    verse = t.third,
                    payload = payload,
                ),
            )
        }
    }

    private fun migrateVerseCommentaryApi(root: File, dao: StudyDao) {
        val dir = File(root, "verse_commentary_api")
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".json")) return@forEach
            val parsed = parseApiCacheFileBase(f.nameWithoutExtension) ?: return@forEach
            val payload = try {
                f.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                return@forEach
            }
            if (payload.isBlank()) return@forEach
            dao.upsertVerseBlob(
                StudyVerseBlobEntity(
                    kind = StudyVerseBlobKind.VERSE_COMMENTARY_API,
                    translationCode = parsed.translationCode,
                    bookId = parsed.bookId,
                    chapter = parsed.chapter,
                    verse = parsed.verse,
                    payload = payload,
                ),
            )
        }
    }

    private val File.nameWithoutExtension: String
        get() = name.substringBeforeLast('.')

    private fun parseChapterFileBaseName(base: String): Pair<String, Int>? {
        val idx = base.lastIndexOf('_')
        if (idx <= 0) return null
        val ch = base.substring(idx + 1).toIntOrNull() ?: return null
        val bookId = base.substring(0, idx)
        if (bookId.isBlank()) return null
        return bookId to ch
    }

    private fun parseVerseTripletName(base: String): Triple<String, Int, Int>? {
        val parts = base.split('_')
        if (parts.size < 3) return null
        val verse = parts.last().toIntOrNull() ?: return null
        val chapter = parts[parts.size - 2].toIntOrNull() ?: return null
        val bookId = parts.dropLast(2).joinToString("_")
        if (bookId.isBlank()) return null
        return Triple(bookId, chapter, verse)
    }

    /** Имя файла: `{translationCode}_{bookId}_{chapter}_{verse}` без расширения. */
    private fun parseApiCacheFileBase(base: String): ParsedApiCacheName? {
        val parts = base.split('_')
        if (parts.size < 4) return null
        val verse = parts.last().toIntOrNull() ?: return null
        val chapter = parts[parts.size - 2].toIntOrNull() ?: return null
        val translationCode = parts.first()
        val bookId = parts.drop(1).dropLast(2).joinToString("_")
        if (translationCode.isBlank() || bookId.isBlank()) return null
        return ParsedApiCacheName(translationCode, bookId, chapter, verse)
    }

    private data class ParsedApiCacheName(
        val translationCode: String,
        val bookId: String,
        val chapter: Int,
        val verse: Int,
    )
}
