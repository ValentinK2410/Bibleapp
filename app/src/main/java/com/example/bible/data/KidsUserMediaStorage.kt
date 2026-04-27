package com.example.bible.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/** Сохранение пользовательских медиа для раздела «Детям» в [Context.getFilesDir]. */
object KidsUserMediaStorage {
    const val IMAGES_SUBDIR = "kids_user_images"
    const val SOUNDS_SUBDIR = "kids_user_sounds"

    /** Путь относительно [Context.getFilesDir] или null при ошибке. */
    fun copyUriIntoFilesDir(
        context: Context,
        uri: Uri,
        subdir: String,
        fallbackExtension: String,
    ): String? {
        val cr = context.contentResolver
        val ext = guessExtension(context, uri) ?: fallbackExtension
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val dest = File(dir, name)
        return try {
            cr.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            "$subdir/$name"
        } catch (_: Exception) {
            if (dest.exists()) dest.delete()
            null
        }
    }

    private fun guessExtension(context: Context, uri: Uri): String? {
        val t = context.contentResolver.getType(uri)?.lowercase() ?: return null
        return when {
            t.contains("png") -> "png"
            t.contains("jpeg") || t.contains("jpg") -> "jpg"
            t.contains("webp") -> "webp"
            t.contains("gif") -> "gif"
            t.contains("mpeg") || t.contains("mp3") -> "mp3"
            t.contains("ogg") -> "ogg"
            t.contains("wav") -> "wav"
            t.contains("mp4") || t.contains("audio/mp4") -> "m4a"
            else -> null
        }
    }

    fun displayName(context: Context, uri: Uri): String? {
        val cr = context.contentResolver
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return null
    }
}
