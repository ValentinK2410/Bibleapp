package com.example.bible.data.travel

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Копирование выбранного аудио во внутреннее хранилище (стабильный file:// для фона). */
object TravelUserSoundStorage {
    const val SUBDIR = "travel_user_sounds"

    /** Файл для новой записи с микрофона (в том же каталоге, что и импортированные треки). */
    fun createRecordingOutputFile(context: Context): File {
        val dir = File(context.filesDir, SUBDIR).apply { mkdirs() }
        val name = "rec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.m4a"
        return File(dir, name)
    }

    fun copyUriToFilesDir(context: Context, uri: Uri, fallbackExt: String = "mp3"): String? {
        val cr = context.contentResolver
        val type = cr.getType(uri)?.lowercase().orEmpty()
        val ext = when {
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("ogg") -> "ogg"
            type.contains("wav") -> "wav"
            type.contains("mp4") || type.contains("m4a") || type.contains("aac") -> "m4a"
            else -> fallbackExt
        }
        val dir = File(context.filesDir, SUBDIR).apply { mkdirs() }
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val dest = File(dir, name)
        return try {
            cr.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            dest.absolutePath
        } catch (_: Exception) {
            if (dest.exists()) dest.delete()
            null
        }
    }

    fun toFileUriString(absolutePath: String): String = Uri.fromFile(File(absolutePath)).toString()
}
