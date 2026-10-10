package com.example.bible.data

import android.content.Context
import java.io.File

/** Голос родителя для отдельного абзаца или урока истории. Файлы живут только на устройстве. */
object KidsStoryVoice {
    const val LESSON = "lesson"

    fun paragraphKey(index: Int): String = "p$index"

    fun file(context: Context, storyId: String, key: String): File {
        val dir = File(context.filesDir, "story_voices/$storyId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.m4a")
    }

    fun tempFile(context: Context, storyId: String, key: String): File {
        val dir = File(context.filesDir, "story_voices/$storyId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.tmp.m4a")
    }

    fun has(context: Context, storyId: String, key: String): Boolean {
        val f = file(context, storyId, key)
        return f.exists() && f.length() > 800L
    }
}
