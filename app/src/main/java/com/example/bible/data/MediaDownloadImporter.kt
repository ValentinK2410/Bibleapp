package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Переносит скачанный файл в медиатеку приложения без участия экрана и ViewModel —
 * тем же путём, что и импорт из «Загрузки/Bible». Возвращает текст ошибки или null при успехе.
 */
object MediaDownloadImporter {

    private val mutex = Mutex()

    suspend fun import(
        context: Context,
        file: File,
        title: String,
        sourceUrl: String?,
        asAudio: Boolean,
    ): String? = mutex.withLock {
        val app = context.applicationContext
        if (!file.exists()) return@withLock "Файл не найден"
        val prefs = BiblePreferences(app)
        val fingerprint = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        if (prefs.hasDownloadImportFingerprint(fingerprint)) return@withLock null
        val safeTitle = title.trim().ifBlank {
            file.nameWithoutExtension.ifBlank { "Без названия" }
        }
        val result = if (asAudio) {
            BibleAudioLibrary(app).importFromExternalFile(file)
        } else {
            BibleVideoLibrary(app).importFromExternalFile(file)
        }
        result.fold(
            onSuccess = { storedName ->
                if (asAudio) {
                    prefs.saveBibleAudio(
                        BibleUserAudio(
                            title = safeTitle,
                            fileName = storedName,
                            source = "download",
                            sourceUrl = sourceUrl,
                        ),
                    )
                } else {
                    prefs.saveBibleVideo(
                        BibleUserVideo(
                            title = safeTitle,
                            fileName = storedName,
                            source = "download",
                            sourceUrl = sourceUrl,
                        ),
                    )
                }
                prefs.addDownloadImportFingerprint(fingerprint)
                null
            },
            onFailure = { e -> e.message ?: "Ошибка импорта" },
        )
    }
}
