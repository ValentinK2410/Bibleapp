package com.example.bible.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Голос родителя для отдельного абзаца, урока или всей истории. Файлы живут только на устройстве. */
object KidsStoryVoice {
    const val LESSON = "lesson"
    const val WHOLE = "whole"

    fun paragraphKey(index: Int): String = "p$index"

    fun file(context: Context, storyId: String, key: String): File =
        stored(context, storyId, key) ?: recordingDestination(context, storyId, key)

    fun recordingDestination(context: Context, storyId: String, key: String): File {
        val dir = dir(context, storyId)
        return File(dir, "$key.m4a")
    }

    fun tempFile(context: Context, storyId: String, key: String): File {
        val dir = dir(context, storyId)
        return File(dir, "$key.tmp.m4a")
    }

    fun has(context: Context, storyId: String, key: String): Boolean {
        val f = stored(context, storyId, key) ?: return false
        return f.length() > 800L
    }

    fun clear(context: Context, storyId: String, key: String) {
        dir(context, storyId).listFiles()?.forEach { file ->
            if (belongs(file, key)) file.delete()
        }
    }

    /** Кладёт файл с телефона на место голоса этого фрагмента. */
    fun importUri(context: Context, storyId: String, key: String, uri: Uri): Boolean {
        val ext = extension(context, uri)
        val dest = File(dir(context, storyId), "$key.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (dest.length() <= 800L) {
                dest.delete()
                false
            } else {
                dir(context, storyId).listFiles()?.forEach { file ->
                    if (file != dest && belongs(file, key)) file.delete()
                }
                true
            }
        } catch (_: Exception) {
            if (dest.exists()) dest.delete()
            false
        }
    }

    private fun stored(context: Context, storyId: String, key: String): File? =
        dir(context, storyId).listFiles()?.firstOrNull { belongs(it, key) && it.length() > 800L }

    private fun belongs(file: File, key: String): Boolean =
        file.isFile && file.name.startsWith("$key.") && !file.name.contains(".tmp.")

    private fun dir(context: Context, storyId: String): File =
        File(context.filesDir, "story_voices/$storyId").apply { mkdirs() }

    private fun extension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            type.contains("mpeg") || type.contains("mp3") -> "mp3"
            type.contains("wav") -> "wav"
            type.contains("ogg") -> "ogg"
            type.contains("aac") -> "aac"
            type.contains("3gpp") -> "3gp"
            else -> "m4a"
        }
    }
}
